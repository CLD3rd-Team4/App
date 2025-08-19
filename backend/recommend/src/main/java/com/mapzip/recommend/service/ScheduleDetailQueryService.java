package com.mapzip.recommend.service;


import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ScheduleDetailQueryService {

 private final StringRedisTemplate redis;
 private final Gson gson = new Gson();
 private static final Type LIST_STRING = new TypeToken<List<String>>() {}.getType();

 public Optional<Snapshot> get(String userId, String scheduleId) {
     String key = "scheduleDetail:" + userId + ":" + scheduleId;
     if (!Boolean.TRUE.equals(redis.hasKey(key))) return Optional.empty();

     HashOperations<String, String, String> ops = redis.opsForHash();
     String departureTime        = ops.get(key, "departureTime");
     String departureName        = ops.get(key, "departureName");
     String destinationName      = ops.get(key, "destinationName");
     String estimatedArrivalTime = ops.get(key, "estimatedArrivalTime");
     String waypointNamesJson    = ops.get(key, "waypointNames");
     String waypointTimesJson    = ops.get(key, "waypointTimes");

     List<String> waypointNames = parseList(waypointNamesJson);
     List<String> waypointTimes = parseList(waypointTimesJson);

     return Optional.of(Snapshot.builder()
             .departureTime(nvl(departureTime))
             .departureName(nvl(departureName))
             .destinationName(nvl(destinationName))
             .estimatedArrivalTime(nvl(estimatedArrivalTime))
             .waypointNames(waypointNames)
             .waypointTimes(waypointTimes)
             .build());
 }

 private List<String> parseList(String json) {
     if (json == null || json.isBlank()) return Collections.emptyList();
     try {
         return gson.fromJson(json, LIST_STRING);
     } catch (Exception e) {
         return Collections.emptyList();
     }
 }

 private String nvl(String s) { return (s == null) ? "" : s; }

 @Getter
 @Builder
 public static class Snapshot {
     private final String departureTime;
     private final String departureName;
     private final String destinationName;
     private final String estimatedArrivalTime;
     private final List<String> waypointNames;
     private final List<String> waypointTimes;
 }
}
