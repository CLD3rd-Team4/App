package com.mapzip.recommend.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapzip.recommend.dto.RecommendResultDto;
import com.mapzip.recommend.service.RecommendRedisStoreService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecommendResultConsumer {
	private final ObjectMapper objectMapper;
	private final RecommendRedisStoreService recommendRedisStoreService;

    @KafkaListener(topics = "recommend-result", groupId = "recommend-result-group")
    private void consume(String message) {
        try {
            // 로그로 수신 확인
            RecommendResultDto recommendResultDto = objectMapper.readValue(message, RecommendResultDto.class);
            log.info("📩 recommend-request 토픽 수신: userId={}, scheduleId={}",
            		recommendResultDto.getUserId(),recommendResultDto.getScheduleId() );
            recommendRedisStoreService.storeRecommendations(
            		recommendResultDto.getUserId(),
            	    recommendResultDto.getScheduleId(),
            	    recommendResultDto.getRecommendPlaceListJson(),
            	    recommendResultDto.getRecommendationRequestIds()
        
            	);


        } catch (Exception e) {
            log.error("❌ recommend-request 처리 중 오류", e);
        }
    }
}
