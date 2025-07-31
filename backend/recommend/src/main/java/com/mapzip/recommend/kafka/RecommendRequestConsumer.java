package com.mapzip.recommend.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapzip.recommend.dto.RecommendRequestDto;
import com.mapzip.recommend.dto.RecommendResultDto;
import com.mapzip.recommend.service.RecommendService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private static final String NEXT_TOPIC = "recommend-result";

    @KafkaListener(topics = "recommend-request", groupId = "recommend-request-group")
    private void consume(String message) {
        try {
            // 로그로 수신 확인
            RecommendRequestDto recommendRequestDto = objectMapper.readValue(message, RecommendRequestDto.class);
            log.info("📩 recommend-request 토픽 수신: userId={}, scheduleId={}",
            		recommendRequestDto.getUserId(),recommendRequestDto.getScheduleId() );
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
