package com.mapzip.recommend.service;

import java.util.UUID;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapzip.recommend.config.GrpcHeaderConfig;
import com.mapzip.recommend.dto.tmap.TmapScheduleRequest;
import com.mapzip.recommend.dto.tmap.TmapScheduleRequest.RecommendUpdateContext;
import com.mapzip.recommend.grpc.RecommendRequest;
import com.mapzip.recommend.mapper.TmapRequestMapper;
import com.mapzip.schedule.grpc.GetScheduleDetailRequest;
import com.mapzip.schedule.grpc.GetScheduleDetailResponse;
import com.mapzip.schedule.grpc.ScheduleServiceGrpc;

import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendRequestService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String REQUEST_TOPIC = "recommend-request"; 
    private static final String RESULT_TOPIC  = "recommend-result";

    private final ScheduleServiceGrpc.ScheduleServiceBlockingStub scheduleStub;

    public void sendRecommendRequest(RecommendRequest grpcReq) {
        // 0) userId (메타데이터에서 가져옴)
        String userId = GrpcHeaderConfig.UserIdContext.USER_ID.get();

        // 1) gRPC 요청에서 값 꺼내기 (optional 필드 대응)
        String scheduleId   = grpcReq.getScheduleId();
        String clientNowIso = grpcReq.hasClientNowIso() ? grpcReq.getClientNowIso() : null;
        Double currentLat   = grpcReq.hasCurrentLat()   ? grpcReq.getCurrentLat()   : null;
        Double currentLng   = grpcReq.hasCurrentLng()   ? grpcReq.getCurrentLng()   : null;
        boolean IsUpdate=false;
        if (clientNowIso != null && !clientNowIso.isBlank()
                && currentLat != null
                && currentLng != null) {
        	
        	IsUpdate=true;
        } else {
           IsUpdate=false;
        }

        // 2) 캐시 히트면 결과 토픽 바로 발행
        String cacheKey = String.format("scheduleDetail:%s:%s", userId, scheduleId);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))&& IsUpdate==false) {
            kafkaTemplate.send(RESULT_TOPIC, scheduleId, userId);
            log.info("➡ 캐시 히트: {} 토픽 즉시 전송 완료 scheduleId={}, userId={}", RESULT_TOPIC, scheduleId, userId);
            return;
        }

        // 3) 스케줄 서버 조회 (헤더에 x-user-id 부착)
        GetScheduleDetailRequest scheduleReq = GetScheduleDetailRequest.newBuilder()
                .setScheduleId(scheduleId)
                .build();

        Metadata md = new Metadata();
        Metadata.Key<String> USER_ID_KEY = Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER);
        if (userId != null && !userId.isBlank()) {
            md.put(USER_ID_KEY, userId);
        }
        ScheduleServiceGrpc.ScheduleServiceBlockingStub stubWithMd =
                scheduleStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(md));

        GetScheduleDetailResponse scheduleRes = stubWithMd.getScheduleDetail(scheduleReq);

        // 4) Tmap 요청 매핑 
        // 추천 업데이트일 경우 isUpdate=true 
        TmapScheduleRequest tmapScheduleRequest = TmapRequestMapper.fromScheduleDetail(
                scheduleRes.getSchedule(),
                scheduleId,
                userId,
                clientNowIso,
                currentLat,
                currentLng,
                IsUpdate
        );

        // 5) Kafka 전송 (키는 scheduleId로 파티셔닝)
        try {
            String payload = objectMapper.writeValueAsString(tmapScheduleRequest);
            kafkaTemplate.send(REQUEST_TOPIC, scheduleId, payload);
            log.info("➡ 새로운 스케줄 결과 : {} 토픽 전송 완료 scheduleId={}, userId={}", REQUEST_TOPIC, scheduleId, userId);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize TmapScheduleRequest", e);
        }
    }
}
