package com.mapzip.recommend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapzip.recommend.dto.tmap.MealSlotData;
import com.mapzip.recommend.dto.tmap.TmapScheduleRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateScheduleRequestBuilder {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final Pattern KOREAN_AMPM = Pattern.compile("^(오전|오후)\\s*(\\d{1,2}):(\\d{2})$");
    private static final Pattern H24         = Pattern.compile("^(\\d{1,2}):(\\d{2})$");

    public TmapScheduleRequest build(TmapScheduleRequest request) {
        if (request.getRecommendUpdateContext() == null
                || !Boolean.TRUE.equals(request.getRecommendUpdateContext().getIsUpdate())) {
            throw new IllegalArgumentException("UpdateScheduleRequestBuilder: isUpdate=true 인 경우에만 사용하세요.");
        }

        var ctx = request.getRecommendUpdateContext();

        // 1) referenceNow 계산: departureTime(오늘로 파싱) vs now
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime departureLdt = parseToTodayStrict(request.getDepartureTime(), today);
        LocalDateTime referenceNow = (departureLdt == null) ? now : (departureLdt.isBefore(now) ? now : departureLdt);

        // 2) 출발지: 현재 위치로 교체
        TmapScheduleRequest.LocationDto dep = new TmapScheduleRequest.LocationDto();
        dep.setName("현재 위치");
        dep.setLat(String.valueOf(ctx.getCurrentLat()));
        dep.setLng(String.valueOf(ctx.getCurrentLng()));
        request.setDeparture(dep);

        // 3) departureTime 비었으면 현재시각 "오전/오후 HH:mm"으로 채움(프론트 가독성)
        if (isBlank(request.getDepartureTime())) {
            request.setDepartureTime(formatKoreanAmPm(now));
        }

        // 4) 경유지: waypointTimes를 오늘로 파싱 → 출발보다 이르면 무조건 +1일 보정 → referenceNow 이후만 남김
        request.setWaypoints(filterFutureWaypointsToLocationDtos(request, referenceNow, today));

        // 5) 식사 슬롯: 동일 규칙
        request.setMealSlots(filterFutureMealSlots(request.getMealSlots(), referenceNow, today));

        return request;
    }

    // ------------------- Waypoints -------------------
    private List<TmapScheduleRequest.LocationDto> filterFutureWaypointsToLocationDtos(
            TmapScheduleRequest request,
            LocalDateTime referenceNow,
            LocalDate today
    ) {
        List<TmapScheduleRequest.LocationDto> out = new ArrayList<>();
        var original = request.getWaypoints();
        if (original == null || original.isEmpty()) return out;

        String cacheKey = String.format("scheduleDetail:%s:%s", request.getUserId(), request.getScheduleId());
        String waypointTimesJson = (String) redisTemplate.opsForHash().get(cacheKey, "waypointTimes");
        List<String> waypointTimes = parseStringArraySafe(waypointTimesJson);

        for (int i = 0; i < original.size(); i++) {
            String when = (waypointTimes != null && i < waypointTimes.size()) ? waypointTimes.get(i) : null;
            if (isBlank(when)) continue;

            LocalDateTime eta = parseToTodayStrict(when, today);
            if (eta == null) {
                log.warn("waypoint 시간 파싱 실패: when={}", when);
                continue;
            }

            // ✅ 출발보다 이르면 무조건 익일로 보정
            if (eta.isBefore(referenceNow)) {
                eta = eta.plusDays(1);
            }

            if (!eta.isBefore(referenceNow)) {
                out.add(original.get(i));
            }
        }
        return out;
    }

    // ------------------- MealSlots -------------------
    private List<MealSlotData> filterFutureMealSlots(
            List<MealSlotData> slots,
            LocalDateTime referenceNow,
            LocalDate today
    ) {
        if (slots == null || slots.isEmpty()) return List.of();

        List<MealSlotData> out = new ArrayList<>();
        for (MealSlotData s : slots) {
            String t = s.getScheduledTime();
            if (isBlank(t)) {
                log.warn("meal slot time 비어있음: slotId={}", s.getSlotId());
                continue; // 정책상 제외
            }
            LocalDateTime slotTime = parseToTodayStrict(t, today);
            if (slotTime == null) {
                log.warn("meal slot time parse 실패: slotId={}, scheduledTime={}", s.getSlotId(), t);
                continue; // 정책상 제외
            }

            // ✅ 출발보다 이르면 무조건 익일로 보정
            if (slotTime.isBefore(referenceNow)) {
                slotTime = slotTime.plusDays(1);
            }

            if (!slotTime.isBefore(referenceNow)) {
                out.add(s);
            }
        }
        log.info("mealSlots filtered: before={}, after={}", (slots != null ? slots.size() : 0), out.size());
        return out;
    }

    // ------------------- 시간 유틸 -------------------

    /**
     * "오전/오후 HH:mm" 또는 "HH:mm"을 '오늘 날짜'로 파싱(롤오버 금지). 실패 시 null.
     */
    private LocalDateTime parseToTodayStrict(String timeText, LocalDate baseDate) {
        if (isBlank(timeText)) return null;

        // 1) 오전/오후 HH:mm
        Matcher m = KOREAN_AMPM.matcher(timeText.trim());
        if (m.matches()) {
            String ampm = m.group(1);
            int hour = parseIntSafe(m.group(2), -1);
            int minute = parseIntSafe(m.group(3), -1);
            if (!isHourMinuteValid(hour, minute)) return null;

            if ("오전".equals(ampm)) {
                if (hour == 12) hour = 0;        // 오전 12시 → 00시
            } else { // 오후
                if (hour != 12) hour += 12;      // 오후 1~11 → 13~23
            }
            return LocalDateTime.of(baseDate, LocalTime.of(hour, minute));
        }

        // 2) HH:mm (24시간제)
        m = H24.matcher(timeText.trim());
        if (m.matches()) {
            int hour = parseIntSafe(m.group(1), -1);
            int minute = parseIntSafe(m.group(2), -1);
            if (!isHourMinuteValid(hour, minute)) return null;
            return LocalDateTime.of(baseDate, LocalTime.of(hour, minute));
        }

        return null;
    }

    // ------------------- 기타 유틸 -------------------
    private List<String> parseStringArraySafe(String json) {
        if (isBlank(json)) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("waypointTimes JSON 파싱 실패: {}", json);
            return List.of();
        }
    }

    private static boolean isHourMinuteValid(int hour, int minute) {
        return (hour >= 0 && hour <= 23) && (minute >= 0 && minute <= 59);
    }

    private static int parseIntSafe(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (Exception e) { return fallback; }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String formatKoreanAmPm(LocalDateTime ldt) {
        int hour = ldt.getHour();
        int minute = ldt.getMinute();
        String ampm = (hour < 12) ? "오전" : "오후";
        int h12 = hour % 12;
        if (h12 == 0) h12 = 12;
        return String.format("%s %d:%02d", ampm, h12, minute);
    }
}
