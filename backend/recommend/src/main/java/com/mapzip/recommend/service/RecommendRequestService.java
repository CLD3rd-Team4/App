package com.mapzip.recommend.service;

import java.util.UUID;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapzip.recommend.dto.MultiSlotRecommendRequestDto;
import com.mapzip.recommend.dto.RecommendRequestDto;
import com.mapzip.recommend.dto.tmap.TmapScheduleRequest;
import com.mapzip.recommend.mapper.TmapRequestMapper;
import com.mapzip.recommend.mock.MockTmapScheduleRequestBuilder;
import com.mapzip.schedule.grpc.GetScheduleDetailRequest;
import com.mapzip.schedule.grpc.GetScheduleDetailResponse;
import com.mapzip.schedule.grpc.ScheduleServiceGrpc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendRequestService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private static final String REQUEST_TOPiC = "recommend-request";
    private static final String RESULT_TOPIC = "recommend-result";
    private final ScheduleServiceGrpc.ScheduleServiceBlockingStub scheduleStub;
    
    public void sendRecommendRequest(String scheduleId) {
            // userid 목데이터 
            String userId = "user123";
            
            //1. 저장된 스케줄이라면 추천 과정 건너뛰기 
            String cacheKey = String.format("scheduleDetail:%s:%s", userId, scheduleId);
            if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) {
                // 캐시 있으면 완료 알림만 발행 (key=scheduleId, value=userId)
                kafkaTemplate.send(RESULT_TOPIC, scheduleId, userId);
                log.info("➡ 저장된 스케줄 결과 : {} 토픽으로 즉시 전송 완료 scheduleId={}, userId={}", RESULT_TOPIC, scheduleId, userId);
                return;
            }
            
            //2. 스케줄 서버로부터 스케줄 정보 받음 
            GetScheduleDetailRequest request = GetScheduleDetailRequest.newBuilder()
                    .setScheduleId(scheduleId)
                    .setUserId(userId)
                    .build();

            // gRPC로 스케줄 조회 -> 리뷰서버랑 연결 
//            GetScheduleDetailResponse response = scheduleStub.getScheduleDetail(request);
//            TmapScheduleRequest tmapScheduleRequest = TmapRequestMapper.fromScheduleDetail(response.getSchedule(), scheduleId, userId);
            //목데이터 -> 빼야
            TmapScheduleRequest tmapScheduleRequest=MockTmapScheduleRequestBuilder.buildMock();
            
            
            //3. 프론트한테 스케줄 요약 보내는 gRPC
            
            //4. Kafka 전송 
            String payload;
            try {
                payload = objectMapper.writeValueAsString(tmapScheduleRequest);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize TmapScheduleRequest", e);
            }
            log.info("➡ 새로운 스케줄 결과 :{} 토픽으로 즉시 전송 완료 scheduleId={}, userId={}", REQUEST_TOPiC, scheduleId, userId);
            kafkaTemplate.send(REQUEST_TOPiC, scheduleId, payload);
            
    }
    
    
}
