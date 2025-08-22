package com.mapzip.recommend.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
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

	@KafkaListener(topics = "recommend-result", groupId = "recommend-service-result")
	private void consume(ConsumerRecord<String, String> record) {
		try {
			// 로그로 수신 확인
			String scheduleId = record.key();
		    String userId = record.value();
		    log.info("📩 recommend-result 수신: userId={}, scheduleId={}", userId, scheduleId);

		} catch (Exception e) {
			log.error("❌ recommend-result 처리 중 오류", e);
		}
	}
}
