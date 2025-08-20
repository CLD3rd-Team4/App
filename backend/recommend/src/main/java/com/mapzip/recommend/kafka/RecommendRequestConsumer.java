package com.mapzip.recommend.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mapzip.recommend.cache.ScheduleDetailCache;
import com.mapzip.recommend.dto.MultiSlotRecommendRequestDto;
import com.mapzip.recommend.dto.RecommendRequestDto;
import com.mapzip.recommend.dto.RecommendResultDto;
import com.mapzip.recommend.dto.SlotContext;
import com.mapzip.recommend.dto.kakao.Document;
import com.mapzip.recommend.dto.kakao.KakaoSearchResponse;
import com.mapzip.recommend.dto.tmap.MealSlotData;
import com.mapzip.recommend.dto.tmap.TmapScheduleRequest;
import com.mapzip.recommend.mapper.RecommendRequestMapper;
import com.mapzip.recommend.mapper.TmapRequestMapper;
import com.mapzip.recommend.mapper.TmapResultMapper;
import com.mapzip.recommend.mock.MockTmapScheduleRequestBuilder;
import com.mapzip.recommend.service.CleanupDbService;
import com.mapzip.recommend.service.KakaoApiService;
import com.mapzip.recommend.service.RecommendRedisStoreService;
import com.mapzip.recommend.service.RecommendService;
import com.mapzip.recommend.service.TmapRouteCalculator;
import com.mapzip.recommend.service.UpdateScheduleRequestBuilder;
import com.mapzip.schedule.grpc.GetScheduleDetailRequest;
import com.mapzip.schedule.grpc.GetScheduleDetailResponse;
import com.mapzip.schedule.grpc.ScheduleServiceGrpc;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecommendRequestConsumer {

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RecommendService recommendService;
    private final KakaoApiService kakaoApiService;
    private final ScheduleServiceGrpc.ScheduleServiceBlockingStub scheduleStub;
    private final TmapRouteCalculator tmapRouteCalculator;
    private final StringRedisTemplate redis;
    private final ScheduleDetailCache scheduleDetailCache;
    private final RecommendRedisStoreService recommendRedisStoreService;
    private final UpdateScheduleRequestBuilder updateScheduleRequestBuilder;
    private final CleanupDbService cleanupDbService;

    private static final String NEXT_TOPIC = "recommend-result";

    @KafkaListener(topics = "recommend-request", groupId = "recommend-service-request")
    private void consume(@Payload String payload) {
        try {
            // 로그로 수신 확인
            log.info("📩 recommend-request 토픽 수신");
            
            TmapScheduleRequest tmapScheduleRequest = objectMapper.readValue(payload, TmapScheduleRequest.class);
            String runId = tmapScheduleRequest.getRunId();
            boolean IsUpdate = tmapScheduleRequest.getRecommendUpdateContext().getIsUpdate();
            if(IsUpdate==true) {
            	tmapScheduleRequest = updateScheduleRequestBuilder.build(tmapScheduleRequest);
            }
            //tmap api 요청 
            Map<String, Object> tmapResult = tmapRouteCalculator.calculate(tmapScheduleRequest);
            String estimatedArrivalTime = String.valueOf(tmapResult.get("estimatedArrivalTime"));
            @SuppressWarnings("unchecked")
            List<String> waypointTimes = (List<String>) tmapResult.get("waypointArrivalTimes");

            //스케줄 db 저장
            scheduleDetailCache.saveScheduleDetail(
                    tmapScheduleRequest,
                    estimatedArrivalTime,
                    waypointTimes,
                    IsUpdate
            );
            
            //tmap 응답 -> 카카오 api 요청 dto
            MultiSlotRecommendRequestDto multiSlotRecommendRequestDto = TmapResultMapper.toDto(tmapResult);
            
            // 카카오에 식당 10개 추천 받기 
            Map<String, KakaoSearchResponse> kakaoResults = kakaoApiService.getSlotRestaurantMap(multiSlotRecommendRequestDto);
            RecommendRequestDto recommendRequestDto=RecommendRequestMapper.toRecommendRequestDto(multiSlotRecommendRequestDto, kakaoResults);
            
            //bedrock에 식당 3개 추천 받기 
            RecommendResultDto recommendResultDto= recommendService.recommendProcess(recommendRequestDto);
            
            
            // 추천 식당 redis에 저장 
            Map<String, SlotContext> slotContextMap = tmapScheduleRequest.getMealSlots().stream()
            	    .collect(Collectors.toMap(
            	        MealSlotData::getSlotId,
            	        m -> new SlotContext(m.getScheduledTime(), m.getMealType()),
            	        (a, b) -> a,                     
            	        java.util.LinkedHashMap::new     
            	    ));
            recommendRedisStoreService.storeRecommendations(
            	    recommendResultDto.getUserId(),
            	    recommendResultDto.getScheduleId(),
            	    recommendResultDto.getRecommendPlaceListJson(),
            	    recommendResultDto.getRecommendationRequestIds(),
            	    slotContextMap,                                   
            	    IsUpdate,
            	    runId
            	);
            // 다른 스케줄 valkey에서 정리 
            cleanupDbService.cleanupUserKeysExceptSchedule(recommendResultDto.getUserId(),recommendResultDto.getScheduleId());
            // scheduleId를 key, userId를 value로 다음 토픽으로 전송
            kafkaTemplate.send("recommend-result", recommendResultDto.getScheduleId(), recommendResultDto.getUserId());
            log.info("➡ recommend-result 발행 완료 ");


        } catch (Exception e) {
            log.error("❌ recommend-request 처리 중 오류", e);
        }
    }
}
