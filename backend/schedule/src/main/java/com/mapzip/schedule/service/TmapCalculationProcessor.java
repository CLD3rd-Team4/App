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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mapzip.schedule.dto.Feature;

@Service
@Slf4j
@RequiredArgsConstructor
public class TmapCalculationProcessor {

    private final ObjectMapper objectMapper;
    private final ScheduleRepository scheduleRepository;
    private final TmapClient tmapClient;
    private final RouteService routeService;
    private final Gson gson = new Gson();

    @Transactional
    public Schedule calculateAndSave(Map<String, Object> jobData) {
        try {
            String scheduleId = (String) jobData.get("scheduleId");
            log.info("동기 처리 시작: scheduleId={}", scheduleId);
            Schedule schedule = scheduleRepository.findById(scheduleId)
                    .orElseThrow(() -> new IllegalArgumentException("스케줄을 찾을 수 없습니다: " + scheduleId));

            TmapRouteRequest tmapRequest = createTmapRequest(jobData);
            TmapRouteResponse tmapResponse = tmapClient.getRoutePrediction(tmapRequest).block();

            if (tmapResponse == null) {
                throw new RuntimeException("Tmap API로부터 응답을 받지 못했습니다.");
            }

            LocalDateTime departureDateTime = TimeUtil.parseKoreanAmPmToFuture((String)jobData.get("departureTime"), LocalDate.now());
            
            List<RouteService.CalculatedLocation> calculatedLocations = routeService.calculateMealLocations(
                    tmapResponse, schedule.getMealTimeSlots(), departureDateTime
            );

            updateScheduleWithTmapResult(schedule, tmapResponse, calculatedLocations, departureDateTime);

            addRecommendationRequest(schedule, calculatedLocations);

            return schedule;

        } catch (Exception e) {
            log.error("Tmap 계산 작업 처리 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("Tmap 계산 중 오류 발생", e);
        }
    }

    private TmapRouteRequest createTmapRequest(Map<String, Object> jobData) {
        Location departureLocation;
        LocalDateTime departureDateTime;

        if ("UPDATE".equals(jobData.get("type"))) {
            log.info("UPDATE 타입 요청: 현재 위치를 기준으로 경로를 계산합니다.");
            departureLocation = convertToObject(jobData.get("departure"), Location.class);
            departureDateTime = LocalDateTime.parse((String) jobData.get("currentTime"));
        } else {
            departureLocation = convertToObject(jobData.get("departure"), Location.class);
            departureDateTime = TimeUtil.parseKoreanAmPmToFuture((String) jobData.get("departureTime"), LocalDate.now());
        }

        Location destinationLocation = convertToObject(jobData.get("destination"), Location.class);
        List<Waypoint> waypointsList = convertToList(jobData.get("waypoints"), Waypoint.class);
        String tmapDepartureTime = TimeUtil.toTmapApiFormat(departureDateTime);
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
        if (clazz.isInstance(obj)) return clazz.cast(obj);
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
        Feature firstFeature = tmapResponse.getFeatures().get(0);
        int totalTimeInSeconds = firstFeature.getProperties().getTotalTime();
        LocalDateTime arrivalDateTime = departureDateTime.plusSeconds(totalTimeInSeconds);
        schedule.setCalculatedArrivalTime(TimeUtil.toKoreanAmPm(arrivalDateTime));

        for (RouteService.CalculatedLocation calculatedLocation : calculatedLocations) {
            MealTimeSlot slot = schedule.getMealTimeSlots().stream()
                    .filter(s -> s.getId().equals(calculatedLocation.getSlotId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Cannot find meal slot for location: " + calculatedLocation.getSlotId()));
            Map<String, Object> locationJson = Map.of("lat", calculatedLocation.getLat(), "lon", calculatedLocation.getLon(), "scheduled_time", slot.getScheduledTime());
            slot.setCalculatedLocation(objectMapper.writeValueAsString(locationJson));
        }

        List<Map<String, Object>> originalWaypoints = objectMapper.readValue(schedule.getWaypoints(), new TypeReference<List<Map<String, Object>>>() {});
        Map<Integer, String> arrivalTimesByIndex = new HashMap<>();
        long accumulatedTime = 0;

        for (Feature feature : tmapResponse.getFeatures()) {
            String featureType = feature.getGeometry().getType();
            Properties properties = feature.getProperties();

            if ("LineString".equalsIgnoreCase(featureType) && properties != null && properties.getTime() != null) {
                accumulatedTime += properties.getTime();
            } else if ("Point".equalsIgnoreCase(featureType) && properties != null && properties.getPointType() != null) {
                String pointType = properties.getPointType();
                if (pointType.startsWith("B")) {
                    try {
                        int waypointNumber = Integer.parseInt(pointType.substring(1));
                        int waypointIndex = waypointNumber - 1; // "B1" -> index 0

                        if (waypointIndex >= 0 && !arrivalTimesByIndex.containsKey(waypointIndex)) {
                            LocalDateTime waypointArrivalTime = departureDateTime.plusSeconds(accumulatedTime);
                            arrivalTimesByIndex.put(waypointIndex, TimeUtil.toKoreanAmPm(waypointArrivalTime));
                            log.info("경유지 인덱스 {} (pointType: {})의 도착 시간 계산 완료: {} (누적 시간: {}초)",
                                    waypointIndex, pointType, TimeUtil.toKoreanAmPm(waypointArrivalTime), accumulatedTime);
                        }
                    } catch (NumberFormatException e) {
                        log.warn("경유지 pointType 형식이 잘못되었습니다: {}", pointType);
                    }
                }
            }
        }
        
        List<Map<String, Object>> newWaypoints = new ArrayList<>();
        for (int i = 0; i < originalWaypoints.size(); i++) {
            Map<String, Object> newWaypoint = new HashMap<>(originalWaypoints.get(i));
            if (arrivalTimesByIndex.containsKey(i)) {
                newWaypoint.put("arrivalTime", arrivalTimesByIndex.get(i));
            }
            newWaypoints.add(newWaypoint);
        }

        schedule.setWaypoints(objectMapper.writeValueAsString(newWaypoints));
        scheduleRepository.save(schedule);
        log.info("스케줄이 Tmap 계산 결과로 업데이트되었습니다: {}", schedule.getId());
    }

    private void addRecommendationRequest(Schedule schedule, List<RouteService.CalculatedLocation> calculatedLocations) {
        log.info("추천 요청이 큐에 추가되었습니다: {}", schedule.getId());
    }
}