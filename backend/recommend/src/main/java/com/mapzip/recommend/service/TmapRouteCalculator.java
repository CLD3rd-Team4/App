package com.mapzip.recommend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.mapzip.recommend.client.TmapClient;
import com.mapzip.recommend.dto.tmap.MealSlotData;
import com.mapzip.recommend.dto.tmap.TmapLocation;
import com.mapzip.recommend.dto.tmap.TmapRouteRequest;
import com.mapzip.recommend.dto.tmap.TmapRouteResponse;
import com.mapzip.recommend.dto.tmap.TmapRoutesInfo;
import com.mapzip.recommend.dto.tmap.TmapScheduleRequest;
import com.mapzip.recommend.dto.tmap.TmapWaypoint;
import com.mapzip.recommend.dto.tmap.WaypointsContainer;
import com.mapzip.recommend.util.TimeUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class TmapRouteCalculator {

    private final TmapClient tmapClient;
    private final RouteService routeService;
    private final Gson gson = new Gson();

    public Map<String, Object> calculate(TmapScheduleRequest request) {
        log.info("🧭 Tmap 경로 계산 시작: scheduleId={}, userId={}", request.getScheduleId(), request.getUserId());

        // 1) Tmap 호출
        TmapRouteRequest tmapRequest = createTmapRequest(request);
        TmapRouteResponse tmapResponse = tmapClient.getRoutePrediction(tmapRequest).block();
        if (tmapResponse == null) throw new RuntimeException("Tmap API 응답이 null입니다.");

        // 2) 기준 출발 시각
        LocalDateTime departureDateTime =
            TimeUtil.parseKoreanAmPmToFuture(request.getDepartureTime(), LocalDate.now());

        // 3) 총 소요시간 + 여유시간(분) → 초
        int totalSec = extractTotalTimeSeconds(tmapResponse);
        int bufferMin = (request.getArrivalBufferMinutes() != null) ? request.getArrivalBufferMinutes() : 0;
        long bufferSec = bufferMin * 60L;

        // 4) 전체 ETA (표시용)
        LocalDateTime eta = departureDateTime.plusSeconds(totalSec + bufferSec);
        String etaKorean = TimeUtil.toKoreanAmPm(eta);

        // 5) 식사 위치 계산
        List<RouteService.CalculatedLocation> calculatedLocations =
            routeService.calculateMealLocations(tmapResponse, request.getMealSlots(), departureDateTime);

        //  경유지 예상 도착시각 계산 
        int waypointCount = (request.getWaypoints() == null) ? 0 : request.getWaypoints().size();
        List<String> waypointArrivalTimes =
            computeWaypointArrivalTimesWithMeals(tmapResponse, departureDateTime, request.getMealSlots(), bufferMin, waypointCount);
        
        // 6) 슬롯 응답 조립
        List<Map<String, Object>> slotResponses = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < calculatedLocations.size(); i++) {
            var loc = calculatedLocations.get(i);
            var slot = request.getMealSlots().get(i);
            slotResponses.add(Map.of(
                "slotId",        loc.getSlotId(),
                "lat",           loc.getLat(),
                "lon",           loc.getLon(),
                "mealType",      slot.getMealType(),
                "scheduledTime", slot.getScheduledTime(),
                "radius",        slot.getRadius()
            ));
        }

        // 7) 최종 응답
        return Map.of(
            "userId", request.getUserId(),
            "scheduleId", request.getScheduleId(),
            "recommendationRequestIds", request.getMealSlots().stream().map(MealSlotData::getSlotId).toList(),
            "userNote", request.getUserNote(),
            "purpose", request.getPurpose(),
            "companions", request.getCompanions(),
            "estimatedArrivalTime", etaKorean,
            "waypointArrivalTimes", waypointArrivalTimes,  
            "slots", slotResponses
        );
    }

    private TmapRouteRequest createTmapRequest(TmapScheduleRequest request) {
        LocalDateTime departureDateTime = TimeUtil.parseKoreanAmPmToFuture(request.getDepartureTime(), LocalDate.now());
        String tmapDepartureTime = TimeUtil.toTmapApiFormat(departureDateTime);

        TmapLocation departure = new TmapLocation(
                request.getDeparture().getName(),
                request.getDeparture().getLng(),
                request.getDeparture().getLat()
        );

        TmapLocation destination = new TmapLocation(
                request.getDestination().getName(),
                request.getDestination().getLng(),
                request.getDestination().getLat()
        );

        WaypointsContainer waypointsContainer = null;
        if (request.getWaypoints() != null && !request.getWaypoints().isEmpty()) {
            List<TmapWaypoint> waypoints = request.getWaypoints().stream()
                    .map(wp -> new TmapWaypoint(String.valueOf(wp.getLng()), String.valueOf(wp.getLat())))
                    .collect(Collectors.toList());
            waypointsContainer = new WaypointsContainer(waypoints);
        }

        TmapRoutesInfo routesInfo = new TmapRoutesInfo(
                departure,
                destination,
                waypointsContainer,
                "departure",
                tmapDepartureTime,
                "00",
                "car"
        );
        return new TmapRouteRequest(routesInfo);
    }

    private int extractTotalTimeSeconds(TmapRouteResponse resp) {
        // 1.tmap 응답 기준으로 도착 예상 시간을 정함 
        try {
            return resp.getFeatures().get(0).getProperties().getTotalTime();
        } catch (Exception ignore) {}

        // 2. tmap 응답이 없을 경우 스케줄 요소들로 도착 예상 시간을 정함 
        int sum = 0;
        if (resp.getFeatures() != null) {
            for (var f : resp.getFeatures()) {
                var props = f.getProperties();
                if (props != null && props.getTime() != null) {
                    sum += props.getTime();
                }
            }
        }
        return sum;
    }

    private List<String> computeWaypointArrivalTimesWithMeals(TmapRouteResponse resp,
                                                              LocalDateTime departureDateTime,
                                                              List<MealSlotData> mealSlots,
                                                              int arrivalBufferMinutes,
                                                              int waypointCount) {
        // 경유지 ETA 결과 리스트 초기화
        List<String> times = new ArrayList<>(Collections.nCopies(waypointCount, ""));
        if (waypointCount == 0 || resp.getFeatures() == null) return times;

        // per-slot 가산 분 (반올림): 도착여유 / 전체 slot 수
        int totalSlots = (mealSlots == null) ? 0 : mealSlots.size();
        int perSlotMinutes = (totalSlots > 0)
                ? Math.round((float) arrivalBufferMinutes / (float) totalSlots)
                : 0;

        // 슬롯 예정 시각을 출발 날짜 기준으로 파싱
        List<LocalDateTime> slotTimes = parseSlotTimes(mealSlots, departureDateTime.toLocalDate());

        long accumulatedSec = 0L;

        for (var feature : resp.getFeatures()) {
            var props = feature.getProperties();
            var geomType = (feature.getGeometry() != null) ? feature.getGeometry().getType() : null;

            // 구간 시간 누적
            if ("LineString".equalsIgnoreCase(geomType) && props != null && props.getTime() != null) {
                accumulatedSec += props.getTime();
                continue;
            }

            // 경유지 포인트 처리
            if ("Point".equalsIgnoreCase(geomType) && props != null && props.getPointType() != null) {
                String pt = props.getPointType(); // "B1", "B2", ...
                if (pt.startsWith("B")) {
                    try {
                        int waypointNumber = Integer.parseInt(pt.substring(1)); // "B1" -> 1
                        int idx = waypointNumber - 1;
                        if (idx >= 0 && idx < times.size() && times.get(idx).isEmpty()) {
                            // 경유지 기본 ETA
                            LocalDateTime baseEta = departureDateTime.plusSeconds(accumulatedSec);

                            // baseEta 이전에 예정된 slot 개수 계산
                            int slotsBefore = countSlotsBefore(slotTimes, baseEta);

                            // 가산 분 = per-slot 분 × slotsBefore
                            int addMinutes = perSlotMinutes * slotsBefore;

                            // 최종 ETA
                            LocalDateTime finalEta = baseEta.plusMinutes(addMinutes);

                            // 한국어 AM/PM 포맷으로 저장
                            times.set(idx, TimeUtil.toKoreanAmPm(finalEta));
                        }
                    } catch (NumberFormatException ignore) {
                        // 예상치 못한 pointType 형식은 스킵
                    }
                }
            }
        }

        return times;
    }

    // slot.scheduledTime("오전 10:00" 등)을 출발 날짜 기준 LocalDateTime으로 변환
    private List<LocalDateTime> parseSlotTimes(List<MealSlotData> mealSlots, LocalDate baseDate) {
        List<LocalDateTime> out = new ArrayList<>();
        if (mealSlots == null) return out;
        for (MealSlotData s : mealSlots) {
            try {
                LocalDateTime t = TimeUtil.parseKoreanAmPmToFuture(s.getScheduledTime(), baseDate);
                out.add(t);
            } catch (Exception e) {
                // 파싱 실패 시 건너뜀
            }
        }
        return out;
    }

    // baseEta 이전(같음 포함)의 슬롯 개수
    private int countSlotsBefore(List<LocalDateTime> slotTimes, LocalDateTime baseEta) {
        int cnt = 0;
        for (LocalDateTime t : slotTimes) {
            if (t.isBefore(baseEta) || t.isEqual(baseEta)) cnt++;
        }
        return cnt;
    }
}
