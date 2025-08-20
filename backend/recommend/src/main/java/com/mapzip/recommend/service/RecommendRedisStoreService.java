package com.mapzip.recommend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mapzip.recommend.kafka.RecommendResultConsumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendRedisStoreService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;


    public void storeRecommendations(
    	    String userId,
    	    String scheduleId,
    	    String recommendPlaceListJson,
    	    List<String> slotIds,
    	    List<String> scheduledTimes,
    	    Map<String, Integer> slotMealTypeMap,
    	    boolean isUpdate,
    	    String runId
    	) {
    	    try {
    	        JsonNode root = objectMapper.readTree(recommendPlaceListJson);
    	        JsonNode recommendations = root.get("recommendations");
    	        if (recommendations == null || !recommendations.isArray()) return;

    	        // ⏰ TTL: 오늘 자정까지
    	        LocalDateTime now = LocalDateTime.now();
    	        LocalDateTime midnight = now.toLocalDate().atStartOfDay().plusDays(1);
    	        long secondsUntilMidnight = Duration.between(now, midnight).getSeconds();

    	        // slotId -> scheduledTime 매핑 (인덱스 의존 제거)
    	        Map<String, String> slotIdToTime = new java.util.HashMap<>();
    	        for (int i = 0; i < slotIds.size(); i++) {
    	            String sid = slotIds.get(i);
    	            String time = (i < scheduledTimes.size()) ? scheduledTimes.get(i) : "";
    	            slotIdToTime.put(sid, time);
    	        }

    	        // 디버그용: 현재 매핑 상태를 한 번 로그로 확인
    	        log.debug("[Recommend][Redis] slotIds={}, scheduledTimes={}, slotIdToTime={}",
    	                slotIds, scheduledTimes, slotIdToTime);

    	        for (int i = 0; i < recommendations.size(); i++) {
    	            JsonNode mealNode = recommendations.get(i);
    	            if (mealNode == null || mealNode.isNull()) continue;

    	            // ⭐ 1순위: 응답 안에 slotId가 있으면 그걸 사용
    	            String slotIdFromNode = mealNode.path("slotId").asText("");
    	            // 2순위: 폴백 - 기존 인덱스 순서(가능하면)
    	            String slotId = !slotIdFromNode.isEmpty()
    	                    ? slotIdFromNode
    	                    : (i < slotIds.size() ? slotIds.get(i) : "");

    	            if (slotId.isEmpty()) {
    	                log.warn("[Recommend][Redis] slotId를 결정할 수 없습니다. i={}, slotIds.size()={}", i, slotIds.size());
    	                continue;
    	            }

    	            // 시간/타입은 slotId 기반으로만 조회 (인덱스 의존 제거)
    	            String scheduledTime = slotIdToTime.getOrDefault(slotId, "");
    	            int mealType = slotMealTypeMap.getOrDefault(slotId, 0);
    	            String mealTypeStr = (mealType == 0) ? "MEAL" : "SNACK";

    	            JsonNode places = mealNode.get("places");
    	            if (places == null || !places.isArray()) {
    	                log.debug("[Recommend][Redis] places 비어있음 slotId={}", slotId);
    	                continue;
    	            }

    	            // isUpdate일 때 해당 슬롯의 기존 추천만 정리
    	            if (isUpdate) {
    	                String slotPattern = String.format("recommend:%s:%s:%s:*", userId, scheduleId, slotId);
    	                var oldKeys = redis.keys(slotPattern);
    	                if (oldKeys != null && !oldKeys.isEmpty()) {
    	                    redis.delete(oldKeys);
    	                    log.debug("[Recommend][Redis] isUpdate→old keys 삭제 slotPattern={}, count={}", slotPattern, oldKeys.size());
    	                }
    	            }

    	            for (int p = 0; p < places.size(); p++) {
    	                JsonNode place = places.get(p);
    	                if (place == null || place.isNull()) continue;

    	                String placeId = place.path("id").asText("");
    	                String placeName = place.path("place_name").asText("");
    	                String reason = place.path("reason").asText("");
    	                String distance = place.path("distance").asText("");
    	                String addressName = place.path("address_name").asText("");
    	                String placeUrl = place.path("place_url").asText("");
    	                double averageRating = place.path("averageRating").asDouble(0.0);
    	                String representativeReview = place.path("representativeReview").asText("");

    	                ObjectNode simplified = objectMapper.createObjectNode();
    	                simplified.put("mealType", mealType);
    	                simplified.put("scheduledTime", scheduledTime);
    	                simplified.put("id", placeId);
    	                simplified.put("placeName", placeName);
    	                simplified.put("reason", reason);
    	                simplified.put("distance", distance);
    	                simplified.put("addressName", addressName);
    	                simplified.put("placeUrl", placeUrl);
    	                simplified.put("averageRating", averageRating);
    	                simplified.put("representativeReview", representativeReview);

    	                String redisKey = String.format(
    	                    "recommend:%s:%s:%s:%s:place%d",
    	                    userId, scheduleId, slotId, mealTypeStr, p + 1
    	                );
    	                redisTemplate.opsForValue().set(
    	                    redisKey, simplified.toString(), secondsUntilMidnight, TimeUnit.SECONDS
    	                );
    	            }

    	            log.debug("[Recommend][Redis] saved slotId={}, mealType={}, places={}",
    	                    slotId, mealTypeStr, places.size());
    	        }

    	        String lastRunKey = "recommend:run:last:" + scheduleId;
    	        redis.opsForValue().set(lastRunKey, runId, secondsUntilMidnight, TimeUnit.SECONDS);
    	        log.info("[Recommend][Redis] last run saved: key={}, runId={}, isUpdate={}", lastRunKey, runId, isUpdate);

    	    } catch (Exception e) {
    	        throw new RuntimeException("❌ Redis 추천 저장 중 오류 발생", e);
    	    }
    	}

}
