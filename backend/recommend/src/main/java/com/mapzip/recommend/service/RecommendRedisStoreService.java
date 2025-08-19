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
            boolean isUpdate
    ) {
        try {
            JsonNode root = objectMapper.readTree(recommendPlaceListJson);
            JsonNode recommendations = root.get("recommendations");
            if (recommendations == null || !recommendations.isArray()) return;

            // TTL: 오늘 자정까지
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime midnight = now.toLocalDate().atStartOfDay().plusDays(1);
            long secondsUntilMidnight = Duration.between(now, midnight).getSeconds();

            for (int mealIndex = 0; mealIndex < recommendations.size(); mealIndex++) {
                // 길이 어긋남 가드 (업데이트 모드에서 일부 슬롯만 내려올 수 있음)
                if (mealIndex >= slotIds.size()) {
                    log.warn("slotIds index overflow: mealIndex={}, slotIds.size()={}", mealIndex, slotIds.size());
                    continue; 
                }

                JsonNode mealNode = recommendations.get(mealIndex);
                JsonNode places = (mealNode != null) ? mealNode.get("places") : null;
                if (places == null || !places.isArray()) continue;

                String slotId = slotIds.get(mealIndex);
                String scheduledTime = (mealIndex < scheduledTimes.size()) ? scheduledTimes.get(mealIndex) : "";

                int mealType = slotMealTypeMap.getOrDefault(slotId, 0);
                String mealTypeStr = (mealType == 0) ? "MEAL" : "SNACK";

                // isUpdate일 때만: 이 슬롯의 기존 추천 키 전부 삭제 (다른 슬롯은 보존)
                if (isUpdate) {
                    String slotPattern = String.format("recommend:%s:%s:%s:*", userId, scheduleId, slotId);
                    var oldKeys = redis.keys(slotPattern);
                    if (oldKeys != null && !oldKeys.isEmpty()) {
                        redis.delete(oldKeys);
                    }
                 
                }

                // 추천 저장
                for (int i = 0; i < places.size(); i++) {
                    JsonNode place = places.get(i);
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
                            userId, scheduleId, slotId, mealTypeStr, i + 1
                    );
                    redisTemplate.opsForValue().set(
                            redisKey, simplified.toString(), secondsUntilMidnight, TimeUnit.SECONDS
                    );
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("❌ Redis 추천 저장 중 오류 발생", e);
        }
    }
}
