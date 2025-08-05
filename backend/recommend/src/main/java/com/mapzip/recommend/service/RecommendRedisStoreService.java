package com.mapzip.recommend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mapzip.recommend.kafka.RecommendResultConsumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
@Service
@RequiredArgsConstructor
@Slf4j
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
                        String place_name = place.get("place_name").asText();
                        String reason = place.get("reason").asText();
                        String distance = place.get("distance").asText();
                        String address_name=place.get("address_name").asText();
                        String place_url=place.get("place_url").asText();

                        ObjectNode simplified = objectMapper.createObjectNode();
                        simplified.put("mealType", "MEAL"); 
                        simplified.put("scheduledTime", scheduledTime);
                        simplified.put("id", placeId);
                        simplified.put("placeName", place_name);
                        simplified.put("reason", reason);
                        simplified.put("distance", distance);
                        simplified.put("addressName", address_name);
                        simplified.put("placeUrl", place_url);   
                    

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

