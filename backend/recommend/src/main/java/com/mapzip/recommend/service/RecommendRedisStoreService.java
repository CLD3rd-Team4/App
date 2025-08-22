package com.mapzip.recommend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mapzip.recommend.dto.SlotContext;
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
//src/main/java/com/mapzip/recommend/service/RecommendRedisStoreService.java
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
     List<String> slotIds, // 폴백용
     Map<String, SlotContext> slotContextMap, // ★ 여기서 전부 해결
     boolean isUpdate,
     String runId
 ) {
     try {
         JsonNode root = objectMapper.readTree(recommendPlaceListJson);
         JsonNode recommendations = root.get("recommendations");
         if (recommendations == null || !recommendations.isArray()) return;

         // TTL: 오늘 자정까지
         LocalDateTime now = LocalDateTime.now();
         LocalDateTime midnight = now.toLocalDate().atStartOfDay().plusDays(1);
         long secondsUntilMidnight = Duration.between(now, midnight).getSeconds();

         log.info("[Recommend][Redis] slotContextMap={}", slotContextMap);

         for (int i = 0; i < recommendations.size(); i++) {
             JsonNode group = recommendations.get(i);
             if (group == null || group.isNull()) continue;

             // 1순위: 응답에 slotId가 있으면 사용
             String slotIdFromNode = group.path("slotId").asText("");
             // 2순위: 폴백 - 기존 인덱스 순서
             String slotId = !slotIdFromNode.isEmpty()
                     ? slotIdFromNode
                     : (i < slotIds.size() ? slotIds.get(i) : "");

             if (slotId.isEmpty()) {
                 log.warn("[Recommend][Redis] slotId 결정 실패. i={}, slotIds.size()={}", i, slotIds.size());
                 continue;
             }

             // ✅ 시간/타입은 slotContextMap에서만 조회
             SlotContext ctx = slotContextMap.get(slotId);
             if (ctx == null) {
                 log.warn("[Recommend][Redis] slotContext 없음 slotId={}", slotId);
                 continue;
             }
             String scheduledTime = ctx.getScheduledTime();
             int mealType = ctx.getMealType();
             String mealTypeStr = (mealType == 0) ? "MEAL" : "SNACK";

             JsonNode places = group.get("places");
             if (places == null || !places.isArray() || places.size() == 0) {
                 log.debug("[Recommend][Redis] places 비어있음 slotId={}", slotId);
                 continue;
             }

             // 업데이트 시 해당 슬롯 키만 정리
             if (isUpdate) {
                 String pattern = String.format("recommend:%s:%s:%s:*", userId, scheduleId, slotId);
                 var oldKeys = redis.keys(pattern);
                 if (oldKeys != null && !oldKeys.isEmpty()) {
                     redis.delete(oldKeys);
                     log.debug("[Recommend][Redis] isUpdate→old keys 삭제 pattern={}, count={}", pattern, oldKeys.size());
                 }
             }

             for (int p = 0; p < places.size(); p++) {
                 JsonNode place = places.get(p);
                 if (place == null || place.isNull()) continue;

                 ObjectNode simplified = objectMapper.createObjectNode();
                 simplified.put("mealType", mealType);
                 simplified.put("scheduledTime", scheduledTime);
                 simplified.put("id", place.path("id").asText(""));
                 simplified.put("placeName", place.path("place_name").asText(""));
                 simplified.put("reason", place.path("reason").asText(""));
                 simplified.put("distance", place.path("distance").asText(""));
                 simplified.put("addressName", place.path("address_name").asText(""));
                 simplified.put("placeUrl", place.path("place_url").asText(""));
                 simplified.put("averageRating", place.path("averageRating").asDouble(0.0));
                 simplified.put("representativeReview", place.path("representativeReview").asText(""));

                 String redisKey = String.format(
                     "recommend:%s:%s:%s:%s:place%d",
                     userId, scheduleId, slotId, mealTypeStr, p + 1
                 );
                 redisTemplate
                     .opsForValue()
                     .set(redisKey, simplified.toString(), secondsUntilMidnight, TimeUnit.SECONDS);
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
