package com.mapzip.recommend.service;

import java.util.UUID;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapzip.recommend.dto.RecommendRequestDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RecommendRequestService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private static final String TOPIC_NAME = "recommend-request";
    
    public void sendRecommendRequest(RecommendRequestDto recommendRequestDto) {
    	try {
            String json = objectMapper.writeValueAsString(recommendRequestDto);
            kafkaTemplate.send(TOPIC_NAME, json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Kafka 메시지 직렬화 실패", e);
        }

    }
    
    
}
