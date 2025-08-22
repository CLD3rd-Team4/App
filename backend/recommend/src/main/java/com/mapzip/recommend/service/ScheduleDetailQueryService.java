package com.mapzip.recommend.service;


import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;



@Service
@RequiredArgsConstructor
public class ScheduleDetailQueryService {

    private final StringRedisTemplate redis;
    private final Gson gson = new Gson();

    public java.util.Optional<ScheduleSnapshot> get(String userId, String scheduleId) {
        String key = "scheduleDetail:" + userId + ":" + scheduleId;
        Boolean exists = redis.hasKey(key);
        if (exists == null || !exists) return java.util.Optional.empty();

        ScheduleSnapshot s = new ScheduleSnapshot();
        s.setDepartureTime(hget(key, "departureTime"));
        s.setDepartureName(hget(key, "departureName"));
        s.setDestinationName(hget(key, "destinationName"));
        s.setEstimatedArrivalTime(hget(key, "estimatedArrivalTime"));

        s.setWaypointNames(asList(hget(key, "waypointNames")));
        s.setWaypointTimes(asList(hget(key, "waypointTimes")));

        // 업데이트 이력(JSON 문자열) — 저장 시 이미 "오전/오후 HH:mm"
        s.setUpdateLocs(hget(key, "updateLocs")); // null 가능

        return java.util.Optional.of(s);
    }

    private String hget(String key, String field) {
        Object v = redis.opsForHash().get(key, field);
        return v == null ? "" : v.toString();
    }

    private java.util.List<String> asList(String json) {
        if (json == null || json.isBlank()) return java.util.List.of();
        try {
            java.lang.reflect.Type t = new com.google.gson.reflect.TypeToken<java.util.List<String>>(){}.getType();
            java.util.List<String> list = gson.fromJson(json, t);
            return list == null ? java.util.List.of() : list;
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    // 스냅샷 DTO 
    @lombok.Data
    public static class ScheduleSnapshot {
        private String departureTime;
        private String departureName;
        private String destinationName;
        private String estimatedArrivalTime;
        private java.util.List<String> waypointNames;
        private java.util.List<String> waypointTimes;
        private String updateLocs; // JSON 문자열
    }
}
