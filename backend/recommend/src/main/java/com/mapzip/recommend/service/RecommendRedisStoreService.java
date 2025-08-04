package com.mapzip.recommend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
@Service
@RequiredArgsConstructor
public class RecommendRedisStoreService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public void storeRecommendations(String userId, String scheduleId, String recommendPlaceListJson, List<String> slotIds,List<String> scheduledTimes) {
        try {
            JsonNode root = objectMapper.readTree(recommendPlaceListJson);
            JsonNode recommendations = root.get("recommendations");

            if (recommendations != null && recommendations.isArray()) {
                for (int mealIndex = 0; mealIndex < recommendations.size(); mealIndex++) {
                    JsonNode mealNode = recommendations.get(mealIndex);
                    JsonNode places = mealNode.get("places");

                    String slotId = slotIds.get(mealIndex); 
                    String scheduledTime = scheduledTimes.get(mealIndex);

                    for (int i = 0; i < places.size(); i++) {
                        JsonNode place = places.get(i);

                        String placeId = place.get("id").asText();
                        String placeName = place.get("place_name").asText();
                        String reason = place.get("reason").asText();
                        String distance = place.get("distance").asText();

                        ObjectNode simplified = objectMapper.createObjectNode();
                        simplified.put("mealType", "MEAL"); 
                        simplified.put("scheduledTime", scheduledTime);
                        simplified.put("id", placeId);
                        simplified.put("placeName", placeName);
                        simplified.put("reason", reason);
                        simplified.put("distance", distance);
                    

                        String redisKey = String.format("recommend:%s:%s:%s:place%d", userId, scheduleId, slotId, i + 1);
                        redisTemplate.opsForValue().set(redisKey, simplified.toString());
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("❌ Redis 추천 저장 중 오류 발생", e);
        }
    }

}

