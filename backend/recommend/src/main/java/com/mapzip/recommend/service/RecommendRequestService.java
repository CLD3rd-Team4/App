package com.mapzip.recommend.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    private static final String REQUEST_TOPIC = "recommend-request"; 
    private static final String RESULT_TOPIC  = "recommend-result";

    private final ScheduleServiceGrpc.ScheduleServiceBlockingStub scheduleStub;

    public void sendRecommendRequest(RecommendRequest grpcReq) {
        // 0) userId (메타데이터에서 가져옴)
        String userId = GrpcHeaderConfig.UserIdContext.USER_ID.get();

     // 1) gRPC 요청에서 값 꺼내기 (optional 필드 대응)
        final String scheduleId = grpcReq.getScheduleId();

        // presence 체크
        final boolean hasNow = grpcReq.hasClientNowIso() && !grpcReq.getClientNowIso().isBlank();
        final boolean hasLat = grpcReq.hasCurrentLat();
        final boolean hasLng = grpcReq.hasCurrentLng();

        final String clientNowIso = hasNow ? grpcReq.getClientNowIso() : null;
        final Double currentLat   = hasLat ? grpcReq.getCurrentLat()   : null;
        final Double currentLng   = hasLng ? grpcReq.getCurrentLng()   : null;
        final String runId=grpcReq.getRunId();

        // 업데이트 판단: 세 필드 모두 있을 때만 true (정책 유지)
        final boolean isUpdate = hasNow && hasLat && hasLng;

        // 디버그 로그 (좌표/시간이 일부만 있어도 찍어줌)
        log.info("[Recommend] incoming request: scheduleId={}, userId={}, hasNow={}, hasLat={}, hasLng={}, isUpdate={}",
                scheduleId, userId, hasNow, hasLat, hasLng, isUpdate);
        if (hasLat || hasLng) {
            log.info("[Recommend] 추천 업데이트  lat={}, lng={}", currentLat, currentLng);
        }
        if (hasNow) {
            log.info("[Recommend] 추천 업데이트 시간 clientNowIso={}", clientNowIso);
        }

//     // 2) 캐시 히트면 결과 토픽 바로 발행 (단, 업데이트 요청은 무조건 우회)
//        final String cacheKey = String.format("scheduleDetail:%s:%s", userId, scheduleId);
//        final boolean cacheHit = Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey));
//        log.info("[Recommend] cache check: key='{}', hit={}", cacheKey, cacheHit);
//
//        if (cacheHit && !isUpdate) {
//            // 일반 요청 + 캐시 있음 → 즉시 완료 알림
//            kafkaTemplate.send(RESULT_TOPIC, scheduleId, userId);
//            log.info("➡ 캐시 히트(일반요청): {} 토픽 즉시 전송 완료 scheduleId={}, userId={}",
//                    RESULT_TOPIC, scheduleId, userId);
//            String lastRunKey = "recommend:run:last:" + scheduleId;
//            LocalDateTime now = LocalDateTime.now();
//            LocalDateTime midnight = now.toLocalDate().atStartOfDay().plusDays(1);
//            long secondsUntilMidnight = Duration.between(now, midnight).getSeconds();
//            redis.opsForValue().set(lastRunKey, runId, secondsUntilMidnight, TimeUnit.SECONDS);
//            return;
//        } else if (cacheHit && isUpdate) {
//            // 업데이트 요청이면 캐시가 있어도 반드시 재계산 경로로
//            log.info("[Recommend] 업데이트 요청 ");
//        }

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
               isUpdate,
               runId
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
