package com.mapzip.recommend.service;

import java.util.UUID;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapzip.recommend.config.GrpcHeaderConfig;
import com.mapzip.recommend.dto.MultiSlotRecommendRequestDto;
import com.mapzip.recommend.dto.RecommendRequestDto;
import com.mapzip.recommend.dto.tmap.TmapScheduleRequest;
import com.mapzip.recommend.mapper.TmapRequestMapper;
import com.mapzip.recommend.mock.MockTmapScheduleRequestBuilder;
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
	private static final String REQUEST_TOPiC = "recommend-request";
	private static final String RESULT_TOPIC = "recommend-result";
	private final ScheduleServiceGrpc.ScheduleServiceBlockingStub scheduleStub;

	public void sendRecommendRequest(String scheduleId) {
		// userid 목데이터
///         String userId = "user123";
		// 0. userid 가져오기
		String userId = GrpcHeaderConfig.UserIdContext.USER_ID.get();
		String cacheKey = String.format("scheduleDetail:%s:%s", userId, scheduleId);
		if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) {
			// 캐시 있으면 완료 알림만 발행 (key=scheduleId, value=userId)
			kafkaTemplate.send(RESULT_TOPIC, scheduleId, userId);
			log.info("➡ 저장된 스케줄 결과 : {} 토픽으로 즉시 전송 완료 scheduleId={}, userId={}", RESULT_TOPIC, scheduleId, userId);
			return;
		}

		// 2. 스케줄 서버로부터 스케줄 정보 받음
		GetScheduleDetailRequest request = GetScheduleDetailRequest.newBuilder().setScheduleId(scheduleId).build();
		// Metadata에 직접 넣기
		Metadata md = new Metadata();
		Metadata.Key<String> USER_ID_KEY = Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER);
		if (userId != null && !userId.isBlank()) {
			md.put(USER_ID_KEY, userId);
		}
		// stub에 헤더 부착
		ScheduleServiceGrpc.ScheduleServiceBlockingStub stubWithMd = scheduleStub
				.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(md));

		// gRPC로 스케줄 조회 -> 리뷰서버랑 연결
		GetScheduleDetailResponse response = scheduleStub.getScheduleDetail(request);

		TmapScheduleRequest tmapScheduleRequest = TmapRequestMapper.fromScheduleDetail(response.getSchedule(),
				scheduleId, userId);
		// 목데이터
//            TmapScheduleRequest tmapScheduleRequest=MockTmapScheduleRequestBuilder.buildMock();

		// 3. Kafka 전송
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
