package com.mapzip.recommend.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapzip.recommend.dto.MultiSlotRecommendRequestDto;
import com.mapzip.recommend.dto.RecommendRequestDto;
import com.mapzip.recommend.dto.RecommendResultDto;
import com.mapzip.recommend.dto.kakao.Document;
import com.mapzip.recommend.dto.kakao.KakaoSearchResponse;
import com.mapzip.recommend.mapper.RecommendRequestMapper;
import com.mapzip.recommend.service.KakaoApiService;
import com.mapzip.recommend.service.RecommendService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecommendRequestConsumer {

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RecommendService recommendService;
    private final KakaoApiService kakaoApiService;

    private static final String NEXT_TOPIC = "recommend-result";

    @KafkaListener(topics = "recommend-request", groupId = "recommend-request-group")
    private void consume(String message) {
        try {
            // 로그로 수신 확인
        	MultiSlotRecommendRequestDto multiSlotRecommendRequestDto = objectMapper.readValue(message,MultiSlotRecommendRequestDto.class);
            log.info("📩 recommend-request 토픽 수신: userId={}, scheduleId={}",
            		multiSlotRecommendRequestDto.getUserId(),multiSlotRecommendRequestDto.getScheduleId() );
            // 카카오에 식당 10개 추천 받기 
            Map<String, KakaoSearchResponse> kakaoResults = kakaoApiService.getSlotRestaurantMap(multiSlotRecommendRequestDto);
            
            RecommendRequestDto recommendRequestDto=RecommendRequestMapper.toRecommendRequestDto(multiSlotRecommendRequestDto, kakaoResults);
            
            //bedrock에 식당 3개 추천 받기 
            RecommendResultDto recommendResultDto= recommendService.recommendProcess(recommendRequestDto);
            
            // 그대로 다음 토픽으로 전송
            String recommendResult = objectMapper.writeValueAsString(recommendResultDto);
            kafkaTemplate.send(NEXT_TOPIC, recommendResult);
            log.info("➡ ai-bedrock-request 토픽으로 전송 완료");

        } catch (Exception e) {
            log.error("❌ recommend-request 처리 중 오류", e);
        }
    }
}
