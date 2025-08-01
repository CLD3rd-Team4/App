package com.mapzip.schedule.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mapzip.schedule.client.TmapClient;
import com.mapzip.schedule.dto.*;
import com.mapzip.schedule.entity.MealTimeSlot;
import com.mapzip.schedule.entity.Schedule;
import com.mapzip.schedule.grpc.Location;
import com.mapzip.schedule.grpc.Waypoint;
import com.mapzip.schedule.repository.ScheduleRepository;
import com.mapzip.schedule.util.TimeUtil;
import com.mapzip.schedule.util.TimeUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
public class TmapCalculationProcessor {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ScheduleRepository scheduleRepository;
    private final TmapClient tmapClient;
    private final RouteService routeService;
    private final Gson gson = new Gson();

    @Scheduled(fixedDelay = 5000) // 5초마다 실행
    @Transactional
    public void processTmapCalculations() {
        String jobJson = redisTemplate.opsForList().rightPop("tmap:calculations");

        if (jobJson != null) {
            try {
                Map<String, Object> jobData = objectMapper.readValue(jobJson, new TypeReference<>() {});
                String scheduleId = (String) jobData.get("schedule_id");
                Schedule schedule = scheduleRepository.findById(scheduleId)
                        .orElseThrow(() -> new IllegalArgumentException("스케줄을 찾을 수 없습니다: " + scheduleId));

                TmapRouteRequest tmapRequest = createTmapRequest(jobData);
                TmapRouteResponse tmapResponse = tmapClient.getRoutePrediction(tmapRequest).block();

                if (tmapResponse == null) {
                    throw new RuntimeException("Tmap API로부터 응답을 받지 못했습니다.");
                }

                LocalDateTime departureDateTime = TimeUtil.parseKoreanAmPmToFuture((String)jobData.get("departure_time"), LocalDate.now());
                List<RouteService.CalculatedLocation> calculatedLocations = routeService.calculateMealLocations(
                        tmapResponse, schedule.getMealTimeSlots(), departureDateTime
                );

                updateScheduleWithTmapResult(schedule, tmapResponse, calculatedLocations, departureDateTime);

                addRecommendationRequest(schedule, calculatedLocations);

            } catch (Exception e) {
                log.error("Tmap 계산 작업 처리 중 오류 발생: {}", e.getMessage(), e);
            }
        }
    }

    private TmapRouteRequest createTmapRequest(Map<String, Object> jobData) {
        Location departureLocation;
        LocalDateTime departureDateTime;

        // 'UPDATE' 타입인 경우, jobData에서 현재 위치와 시간을 가져와 출발 정보로 사용
        if ("UPDATE".equals(jobData.get("type"))) {
            log.info("UPDATE 타입 요청: 현재 위치를 기준으로 경로를 계산합니다.");
            departureLocation = Location.newBuilder()
                    .setLat(((Number) jobData.get("current_lat")).doubleValue())
                    .setLng(((Number) jobData.get("current_lng")).doubleValue())
                    .setName("현재 위치")
                    .build();
            departureDateTime = LocalDateTime.parse((String) jobData.get("current_time"));
        } else {
            // 'SELECT' 타입인 경우, 기존 스케줄의 출발 정보를 사용
            departureLocation = convertToObject(jobData.get("departure"), Location.class);
            departureDateTime = TimeUtil.parseKoreanAmPmToFuture((String) jobData.get("departure_time"), LocalDate.now());
        }

        Location destinationLocation = convertToObject(jobData.get("destination"), Location.class);
        List<Waypoint> waypointsList = convertToList(jobData.get("waypoints"), Waypoint.class);

        String tmapDepartureTime = TimeUtil.toTmapApiFormat(departureDateTime);

        // Tmap API 요청 객체 생성 (DB의 출발지와는 무관)
        TmapLocation departure = new TmapLocation(departureLocation.getName(), String.valueOf(departureLocation.getLng()), String.valueOf(departureLocation.getLat()));
        TmapLocation destination = new TmapLocation(destinationLocation.getName(), String.valueOf(destinationLocation.getLng()), String.valueOf(destinationLocation.getLat()));

        WaypointsContainer waypointsContainer = null;
        if (waypointsList != null && !waypointsList.isEmpty()) {
            List<TmapWaypoint> waypoints = waypointsList.stream()
                    .map(wp -> new TmapWaypoint(String.valueOf(wp.getLng()), String.valueOf(wp.getLat())))
                    .collect(Collectors.toList());
            waypointsContainer = new WaypointsContainer(waypoints);
        }

        TmapRoutesInfo routesInfo = new TmapRoutesInfo(departure, destination, waypointsContainer, "departure", tmapDepartureTime, "00", "car");
        return new TmapRouteRequest(routesInfo);
    }

    private <T> T convertToObject(Object obj, Class<T> clazz) {
        if (obj == null) return null;
        if (clazz.isInstance(obj)) {
            return clazz.cast(obj);
        }
        String json = gson.toJson(obj);
        return gson.fromJson(json, clazz);
    }

    private <T> List<T> convertToList(Object obj, Class<T> clazz) {
        if (obj == null) return Collections.emptyList();
        if (obj instanceof List) {
            return ((List<?>) obj).stream()
                    .map(item -> convertToObject(item, clazz))
                    .collect(Collectors.toList());
        }
        String json = gson.toJson(obj);
        return gson.fromJson(json, new com.google.gson.reflect.TypeToken<List<T>>() {}.getType());
    }


    private void updateScheduleWithTmapResult(Schedule schedule, TmapRouteResponse tmapResponse, List<RouteService.CalculatedLocation> calculatedLocations, LocalDateTime departureDateTime) throws Exception {
        int totalTimeInSeconds = tmapResponse.getFeatures().get(0).getProperties().getTotalTime();
        LocalDateTime arrivalDateTime = departureDateTime.plusSeconds(totalTimeInSeconds);
        schedule.setCalculatedArrivalTime(TimeUtil.toKoreanAmPm(arrivalDateTime));

        for (RouteService.CalculatedLocation calculatedLocation : calculatedLocations) {
            MealTimeSlot slot = schedule.getMealTimeSlots().stream()
                    .filter(s -> s.getId().equals(calculatedLocation.getSlotId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Cannot find meal slot for location: " + calculatedLocation.getSlotId()));

            Map<String, Object> locationJson = Map.of(
                    "lat", calculatedLocation.getLat(),
                    "lon", calculatedLocation.getLon(),
                    "scheduled_time", slot.getScheduledTime()
            );
            slot.setCalculatedLocation(objectMapper.writeValueAsString(locationJson));
        }

        scheduleRepository.save(schedule);
        log.info("스케줄이 Tmap 계산 결과로 업데이트되었습니다: {}", schedule.getId());
    }

    private void addRecommendationRequest(Schedule schedule, List<RouteService.CalculatedLocation> calculatedLocations) {
        // TODO: 추천 요청 큐에 추가하는 로직 구현
        log.info("추천 요청이 큐에 추가되었습니다: {}", schedule.getId());
    }

    @Scheduled(fixedDelay = 3000)
    public void processRecommendationRequests() {
        // TODO: 추천서버 gRPC 호출 처리
    }
}