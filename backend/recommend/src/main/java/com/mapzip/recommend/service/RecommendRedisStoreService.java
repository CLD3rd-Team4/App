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

    public void storeRecommendations(String userId, String scheduleId, 
    		String recommendPlaceListJson, List<String> slotIds,List<String> scheduledTimes,
    		Map<String, Integer> slotMealTypeMap) 
    {
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
                        Double averageRating=place.get("averageRating").asDouble();
                        String representativeReview=place.get("representativeReview").asText();
                        int mealType = slotMealTypeMap.getOrDefault(slotId, 0);
                        String mealTypeStr = (mealType == 0) ? "MEAL" : "SNACK";
                        
                        

                        ObjectNode simplified = objectMapper.createObjectNode();
                        simplified.put("mealType", mealType); 
                        simplified.put("scheduledTime", scheduledTime);
                        simplified.put("id", placeId);
                        simplified.put("placeName", place_name);
                        simplified.put("reason", reason);
                        simplified.put("distance", distance);
                        simplified.put("addressName", address_name);
                        simplified.put("placeUrl", place_url);   
                        simplified.put("averageRating", averageRating);   
                        simplified.put("representativeReview", representativeReview); 
                        
                    

                        // 오늘 자정까지 남은 시간 계산
                        LocalDateTime now = LocalDateTime.now();
                        LocalDateTime midnight = now.toLocalDate().atStartOfDay().plusDays(1);
                        long secondsUntilMidnight = Duration.between(now, midnight).getSeconds();

                        // Redis 저장 (자정까지 TTL)
                        String redisKey = String.format("recommend:%s:%s:%s:%s:place%d", userId, scheduleId,slotId,mealTypeStr, i + 1);
                        redisTemplate.opsForValue().set(redisKey, simplified.toString(), secondsUntilMidnight, TimeUnit.SECONDS);

                        
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("❌ Redis 추천 저장 중 오류 발생", e);
        }
    }

}

