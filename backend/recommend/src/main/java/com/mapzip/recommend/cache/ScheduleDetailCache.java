package com.mapzip.recommend.cache;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.Date;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mapzip.recommend.dto.tmap.TmapScheduleRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScheduleDetailCache {

    private final StringRedisTemplate redis;
    private final Gson gson = new Gson();

    public void saveScheduleDetail(
            TmapScheduleRequest req,
            String estimatedArrivalTime,
            @Nullable List<String> waypointTimes,
            boolean isUpdate
    ) {
        String key = "scheduleDetail:" + req.getUserId() + ":" + req.getScheduleId();

        if (isUpdate) {
            Boolean exists = redis.hasKey(key);
            if (exists == null || !exists) return;

            // (1) 전체 ETA 갱신
            if (estimatedArrivalTime != null && !estimatedArrivalTime.isBlank()) {
                redis.opsForHash().put(key, "estimatedArrivalTime", estimatedArrivalTime);
            }

            // (2) 경유지 ETA "부분 갱신": 새 값이 온 항목만 바꾸고, 나머지는 유지
            if (waypointTimes != null && !waypointTimes.isEmpty() && req.getWaypoints() != null) {
                // 기존 값 읽기
                String namesJsonOld = (String) redis.opsForHash().get(key, "waypointNames");
                String timesJsonOld = (String) redis.opsForHash().get(key, "waypointTimes");
                List<String> namesOld = (namesJsonOld == null || namesJsonOld.isBlank())
                        ? List.of() : gson.fromJson(namesJsonOld, new com.google.gson.reflect.TypeToken<List<String>>(){}.getType());
                List<String> timesOld = (timesJsonOld == null || timesJsonOld.isBlank())
                        ? List.of() : gson.fromJson(timesJsonOld, new com.google.gson.reflect.TypeToken<List<String>>(){}.getType());

                // 요청으로 들어온 (이후 경유지만 포함된) 이름/시간 쌍을 이름→큐 로 매핑 (중복 이름 대응)
                java.util.Map<String, java.util.Deque<String>> incoming = new java.util.HashMap<>();
                for (int i = 0; i < Math.min(req.getWaypoints().size(), waypointTimes.size()); i++) {
                    String name = nvl(req.getWaypoints().get(i).getName());
                    String time = nvl(waypointTimes.get(i));
                    if (time.isBlank()) continue; // 빈 시간은 무시
                    incoming.computeIfAbsent(name, k -> new java.util.ArrayDeque<>()).add(time);
                }

                // 기존 길이에 맞춰 머지 (없는 인덱스는 빈 문자열로 채움)
                java.util.List<String> timesMerged = new java.util.ArrayList<>(namesOld.size());
                for (int i = 0; i < namesOld.size(); i++) {
                    String keepOrNew = (i < timesOld.size()) ? nvl(timesOld.get(i)) : "";
                    String name = nvl(namesOld.get(i));
                    java.util.Deque<String> q = incoming.get(name);
                    if (q != null && !q.isEmpty()) {
                        keepOrNew = q.pollFirst(); // 해당 이름의 새 값이 있으면 그걸로 덮어씀
                    }
                    timesMerged.add(keepOrNew);
                }

                // 이름은 건드리지 않고, 시간만 갱신
                redis.opsForHash().put(key, "waypointTimes", gson.toJson(timesMerged));
            }

            // (3) updateLocs 누적
            var ctx = req.getRecommendUpdateContext();
            if (ctx != null && Boolean.TRUE.equals(ctx.getIsUpdate())) {
                Object raw = redis.opsForHash().get(key, "updateLocs");
                String existed = (raw == null) ? "[]" : raw.toString();
                java.lang.reflect.Type listType =
                    new com.google.gson.reflect.TypeToken<java.util.List<java.util.Map<String, String>>>() {}.getType();
                java.util.List<java.util.Map<String, String>> updateLocs = gson.fromJson(existed, listType);
                if (updateLocs == null) updateLocs = new java.util.ArrayList<>();

                String destLat = (req.getDestination() != null) ? nvl(req.getDestination().getLat()) : "";
                String destLng = (req.getDestination() != null) ? nvl(req.getDestination().getLng()) : "";

                // 우선순위: clientNowIso → estimatedArrivalTime
                String rawTime = (ctx.getClientNowIso() != null && !ctx.getClientNowIso().isBlank())
                        ? ctx.getClientNowIso()
                        : nvl(estimatedArrivalTime);

                // "오전/오후 HH:mm"으로 정규화
                String displayTime = toKoreanAmPm(rawTime);

                java.util.Map<String, String> entry = new java.util.LinkedHashMap<>();
                entry.put("lat", destLat);
                entry.put("lng", destLng);
                entry.put("time", displayTime); // 항상 오전/오후 HH:mm 형식으로 저장

                updateLocs.add(entry);
                redis.opsForHash().put(key, "updateLocs", gson.toJson(updateLocs));
            }
            return;
        }


        //isUpdate=false일 때 
        List<String> waypointNamesList = (req.getWaypoints() == null) ? List.of() :
                req.getWaypoints().stream()
                        .filter(Objects::nonNull)
                        .map(wp -> nvl(wp.getName()))
                        .collect(Collectors.toList());

        String waypointNamesJson = gson.toJson(waypointNamesList == null ? List.of() : waypointNamesList);
        String waypointTimesJson = gson.toJson(waypointTimes == null ? List.of() : waypointTimes);

        Map<String, String> hash = new LinkedHashMap<>();
        hash.put("departureTime",        nvl(req.getDepartureTime()));
        hash.put("departureName",        (req.getDeparture()   != null) ? nvl(req.getDeparture().getName())   : "");
        hash.put("destinationName",      (req.getDestination() != null) ? nvl(req.getDestination().getName()) : "");
        hash.put("estimatedArrivalTime", nvl(estimatedArrivalTime));
        hash.put("waypointNames",        waypointNamesJson);
        hash.put("waypointTimes",        waypointTimesJson);

        redis.opsForHash().putAll(key, hash);

        // 자정 TTL
        LocalDate today = LocalDate.now();
        Date midnight = Date.from(today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        redis.expireAt(key, midnight);
    }



    // List<String>으로 꺼내는 함수 // 사용안하면 제거 
    public List<String> getWaypointNames(String userId, String scheduleId) {
        return getJsonArrayAsList(userId, scheduleId, "waypointNames");
    }

    public List<String> getWaypointTimes(String userId, String scheduleId) {
        return getJsonArrayAsList(userId, scheduleId, "waypointTimes");
    }

    private List<String> getJsonArrayAsList(String userId, String scheduleId, String field) {
        String key = "scheduleDetail:" + userId + ":" + scheduleId;
        Object raw = redis.opsForHash().get(key, field);
        String json = (raw == null) ? "[]" : raw.toString();
        return gson.fromJson(json, new TypeToken<List<String>>() {}.getType());
    }
    private static String toKoreanAmPm(String input) {
        if (input == null || input.isBlank()) return "";
        // 이미 "오전/오후 HH:mm" 이면 그대로
        if (input.matches("(오전|오후)\\s*\\d{1,2}:\\d{2}")) return input.trim();
        // "HH:mm" 이면 오전/오후로 변환
        if (input.matches("\\d{1,2}:\\d{2}")) {
            String[] sp = input.split(":");
            int h = Integer.parseInt(sp[0]);
            String mm = sp[1];
            String period = (h >= 12) ? "오후" : "오전";
            int display = (h == 0) ? 12 : (h > 12 ? h - 12 : h);
            return period + " " + display + ":" + mm;
        }
        // ISO 같은 날짜/시간이면 파싱 후 변환 (Asia/Seoul 기준)
        try {
            java.time.Instant inst = java.time.Instant.parse(input.trim());
            java.time.ZonedDateTime zdt = inst.atZone(java.time.ZoneId.of("Asia/Seoul"));
            int h = zdt.getHour();
            int m = zdt.getMinute();
            String period = (h >= 12) ? "오후" : "오전";
            int display = (h == 0) ? 12 : (h > 12 ? h - 12 : h);
            String mm = String.format("%02d", m);
            return period + " " + display + ":" + mm;
        } catch (Exception ignored) {
            // 그 외 포맷은 그대로 반환(최소 훼손)
            return input.trim();
        }
    }

    private String nvl(String s) { return (s == null) ? "" : s; }

}
