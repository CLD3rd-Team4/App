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
            @Nullable List<String> waypointTimes   // List 그대로 받음
    ) {
        String key = "scheduleDetail:" + req.getUserId() + ":" + req.getScheduleId();

        // 경유지 이름 List 만들기
        List<String> waypointNamesList = (req.getWaypoints() == null) ? List.of() :
                req.getWaypoints().stream()
                        .filter(Objects::nonNull)
                        .map(wp -> nvl(wp.getName()))
                        .collect(Collectors.toList());

        // JSON 직렬화 (null 방어: 빈 배열)
        String waypointNamesJson = gson.toJson(waypointNamesList == null ? List.of() : waypointNamesList);
        String waypointTimesJson = gson.toJson(waypointTimes == null ? List.of() : waypointTimes);

        Map<String, String> hash = new LinkedHashMap<>();
        hash.put("departureTime",        nvl(req.getDepartureTime()));
        hash.put("departureName",        (req.getDeparture()   != null) ? nvl(req.getDeparture().getName())   : "");
        hash.put("destinationName",      (req.getDestination() != null) ? nvl(req.getDestination().getName()) : "");
        hash.put("estimatedArrivalTime", nvl(estimatedArrivalTime));

        // JSON 배열 문자열 2종
        hash.put("waypointNames",        waypointNamesJson);
        hash.put("waypointTimes",        waypointTimesJson);

        redis.opsForHash().putAll(key, hash);
        LocalDate today = LocalDate.now();
        Date midnight = Date.from(today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        // Redis 키를 오늘 자정까지 유효하게
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

    private String nvl(String s) {
        return (s == null) ? "" : s;
    }
}
