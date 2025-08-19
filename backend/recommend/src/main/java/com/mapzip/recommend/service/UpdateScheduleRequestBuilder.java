package com.mapzip.recommend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapzip.recommend.dto.tmap.MealSlotData;
import com.mapzip.recommend.dto.tmap.TmapScheduleRequest;
import com.mapzip.recommend.util.TimeUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 스케줄 업데이트시 tmap 요청 형태 변경 
@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateScheduleRequestBuilder {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public TmapScheduleRequest build(TmapScheduleRequest request) {
        if (request.getRecommendUpdateContext() == null
                || !Boolean.TRUE.equals(request.getRecommendUpdateContext().getIsUpdate())) {
            throw new IllegalArgumentException("UpdateScheduleRequestBuilder: isUpdate=true 인 경우에만 사용하세요.");
        }

        var ctx = request.getRecommendUpdateContext();

        // 1) 기준 시각 (clientNowIso -> LDT)
        LocalDateTime referenceNow = parseIsoToLocalDateTime(ctx.getClientNowIso());

        // 2) 출발지/출발시각을 현재로 교체
        TmapScheduleRequest.LocationDto dep = new TmapScheduleRequest.LocationDto();
        dep.setName("현재 위치");
        dep.setLat(String.valueOf(ctx.getCurrentLat()));
        dep.setLng(String.valueOf(ctx.getCurrentLng()));
        request.setDeparture(dep);

        request.setDepartureTime(TimeUtil.toKoreanAmPm(referenceNow));

        // 3) 경유지: Valkey의 waypointTimes 기준으로 현재시각 이후만 남김
        request.setWaypoints(filterFutureWaypointsToLocationDtos(request, referenceNow));

        // 4) mealSlots: 현재시각 이전 슬롯 제거
        request.setMealSlots(filterFutureMealSlots(request.getMealSlots(), referenceNow));

        return request; 
    }

    // 경유지 필터링 (추천 업데이트 시간 이후의 경유지만 남김 )
    private List<TmapScheduleRequest.LocationDto> filterFutureWaypointsToLocationDtos(
            TmapScheduleRequest request, LocalDateTime referenceNow) {

        List<TmapScheduleRequest.LocationDto> out = new ArrayList<>();
        var original = request.getWaypoints();
        if (original == null || original.isEmpty()) return out;

        String cacheKey = String.format("scheduleDetail:%s:%s", request.getUserId(), request.getScheduleId());
        String waypointTimesJson = (String) redisTemplate.opsForHash().get(cacheKey, "waypointTimes");
        List<String> waypointTimes = parseStringArraySafe(waypointTimesJson);

        for (int i = 0; i < original.size(); i++) {
            String when = (waypointTimes != null && i < waypointTimes.size()) ? waypointTimes.get(i) : null;
            if (when == null || when.isBlank()) continue;

            // "오전/오후 HH:mm" → 오늘 날짜 기준 LocalDateTime
            LocalDateTime eta = TimeUtil.parseKoreanAmPmToFuture(when, LocalDate.now());
            if (!eta.isBefore(referenceNow)) {
                out.add(original.get(i));
            }
        }
        return out;
    }

    // 이미 지난 식사 시간 제거 
    private List<MealSlotData> filterFutureMealSlots(List<MealSlotData> slots, LocalDateTime referenceNow) {
        if (slots == null || slots.isEmpty()) return List.of();
        List<MealSlotData> out = new ArrayList<>();
        for (MealSlotData s : slots) {
            try {
                LocalDateTime slotTime = TimeUtil.parseKoreanAmPmToFuture(s.getScheduledTime(), LocalDate.now());
                if (!slotTime.isBefore(referenceNow)) out.add(s);
            } catch (Exception e) {
                log.warn("meal slot time parse 실패: slotId={}, scheduledTime={}", s.getSlotId(), s.getScheduledTime());
                // 정책에 따라 포함/제외 선택. 기본은 제외.
            }
        }
        log.info("mealSlots filtered: before={}, after={}", (slots != null ? slots.size() : 0), out.size());
        return out;
    }

    private LocalDateTime parseIsoToLocalDateTime(String iso) {
        try { return OffsetDateTime.parse(iso).toLocalDateTime(); }
        catch (Exception e) {
            log.warn("clientNowIso 파싱 실패: {} (server now 사용)", iso);
            return LocalDateTime.now();
        }
    }

    private List<String> parseStringArraySafe(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("waypointTimes JSON 파싱 실패: {}", json);
            return List.of();
        }
    }
}
