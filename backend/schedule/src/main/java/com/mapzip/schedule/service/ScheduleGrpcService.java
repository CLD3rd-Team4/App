package com.mapzip.schedule.service;

import com.mapzip.schedule.config.GrpcInterceptorConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapzip.schedule.entity.MealTimeSlot;
import com.mapzip.schedule.entity.Schedule;
import com.mapzip.schedule.grpc.*;
import com.mapzip.schedule.mapper.ScheduleMapper;
import com.mapzip.schedule.repository.MealTimeSlotRepository;
import com.mapzip.schedule.repository.ScheduleRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ScheduleGrpcService extends ScheduleServiceGrpc.ScheduleServiceImplBase {

    private final ScheduleRepository scheduleRepository;
    private final MealTimeSlotRepository mealTimeSlotRepository;
    private final ScheduleMapper scheduleMapper;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;

    // @GrpcClient("recommend-service")
    // private RouteCalculatorServiceGrpc.RouteCalculatorServiceBlockingStub recommendClient;

    @Override
    @Transactional
    public void createSchedule(CreateScheduleRequest request, StreamObserver<CreateScheduleResponse> responseObserver) {
        try {
            Schedule schedule = scheduleMapper.toEntity(request);
            if (schedule.getUserId() == null || schedule.getUserId().isEmpty()) {
                schedule.setUserId("test-user-123");
            }

            List<com.mapzip.schedule.grpc.MealTimeSlot> mealSlotsRequest = request.getMealSlotsList();
            if (mealSlotsRequest != null && !mealSlotsRequest.isEmpty()) {
                List<MealTimeSlot> mealTimeSlotEntities = new ArrayList<>();
                for (com.mapzip.schedule.grpc.MealTimeSlot slotRequest : mealSlotsRequest) {
                    MealTimeSlot mealTimeSlot = new MealTimeSlot();
                    mealTimeSlot.setId(java.util.UUID.randomUUID().toString());
                    mealTimeSlot.setSchedule(schedule);
                    mealTimeSlot.setMealType(slotRequest.getMealType().getNumber());
                    mealTimeSlot.setScheduledTime(slotRequest.getScheduledTime());
                    mealTimeSlot.setRadius(slotRequest.getRadius() > 0 ? slotRequest.getRadius() : 1000);
                    mealTimeSlotEntities.add(mealTimeSlot);
                }
                schedule.getMealTimeSlots().addAll(mealTimeSlotEntities);
            }

            scheduleRepository.save(schedule);

            CreateScheduleResponse response = CreateScheduleResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("스케줄이 성공적으로 생성되었습니다.")
                    .setScheduleId(schedule.getId())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("스케줄 생성 중 오류 발생", e);
            responseObserver.onError(Status.INTERNAL.withDescription("스케줄 생성 중 오류: " + e.getMessage()).withCause(e).asRuntimeException());
        }
    }



    @Override
    @Transactional
    public void updateSchedule(UpdateScheduleRequest request, StreamObserver<GetScheduleDetailResponse> responseObserver) {
        try {
            Schedule schedule = scheduleRepository.findById(request.getScheduleId())
                    .orElseThrow(() -> Status.NOT_FOUND.withDescription("수정할 스케줄을 찾을 수 없습니다: " + request.getScheduleId()).asRuntimeException());

            String userId = GrpcInterceptorConfig.USER_ID_CONTEXT_KEY.get();
            if (!schedule.getUserId().equals(userId)) {
                throw Status.PERMISSION_DENIED.withDescription("이 스케줄을 수정할 권한이 없습니다.").asRuntimeException();
            }

            scheduleMapper.updateEntity(schedule, request);

            if (schedule.getMealTimeSlots() != null) {
                schedule.getMealTimeSlots().clear();
            }
            List<com.mapzip.schedule.grpc.MealTimeSlot> mealSlotsRequest = request.getMealSlotsList();
            if (mealSlotsRequest != null && !mealSlotsRequest.isEmpty()) {
                List<MealTimeSlot> mealTimeSlotEntities = new ArrayList<>();
                for (com.mapzip.schedule.grpc.MealTimeSlot slotRequest : mealSlotsRequest) {
                    MealTimeSlot mealTimeSlot = new MealTimeSlot();
                    mealTimeSlot.setId(java.util.UUID.randomUUID().toString());
                    mealTimeSlot.setSchedule(schedule);
                    mealTimeSlot.setMealType(slotRequest.getMealType().getNumber());
                    mealTimeSlot.setScheduledTime(slotRequest.getScheduledTime());
                    mealTimeSlot.setRadius(slotRequest.getRadius() > 0 ? slotRequest.getRadius() : 1000);
                    mealTimeSlotEntities.add(mealTimeSlot);
                }
                schedule.getMealTimeSlots().addAll(mealTimeSlotEntities);
            }

            scheduleRepository.save(schedule);

            GetScheduleDetailResponse response = GetScheduleDetailResponse.newBuilder()
                    .setSchedule(scheduleMapper.toDetail(schedule))
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("스케줄 수정 중 오류 발생", e);
            responseObserver.onError(Status.INTERNAL.withDescription("스케줄 수정 중 오류: " + e.getMessage()).withCause(e).asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void getScheduleList(GetScheduleListRequest request, StreamObserver<GetScheduleListResponse> responseObserver) {
        try {
            List<Schedule> schedules = scheduleRepository.findByUserIdOrderByCreatedAtDesc(request.getUserId());
            List<GetScheduleListResponse.ScheduleSummary> summaries = schedules.stream()
                    .map(scheduleMapper::toSummary)
                    .collect(Collectors.toList());
            GetScheduleListResponse response = GetScheduleListResponse.newBuilder()
                    .addAllSchedules(summaries)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("스케줄 목록 조회 중 오류가 발생했습니다: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void getScheduleDetail(GetScheduleDetailRequest request, StreamObserver<GetScheduleDetailResponse> responseObserver) {
        try {
            String scheduleId = request.getScheduleId();
            Schedule schedule = scheduleRepository.findById(scheduleId)
                    .orElseThrow(() -> new IllegalArgumentException("스케줄을 찾을 수 없습니다."));

            String userId = GrpcInterceptorConfig.USER_ID_CONTEXT_KEY.get();
            if (userId == null || userId.isEmpty()) {
                responseObserver.onError(io.grpc.Status.UNAUTHENTICATED
                        .withDescription("사용자 ID를 확인할 수 없습니다.")
                        .asRuntimeException());
                return;
            }
            
            if (!schedule.getUserId().equals(userId)) {
                responseObserver.onError(io.grpc.Status.PERMISSION_DENIED
                        .withDescription("해당 스케줄에 접근할 권한이 없습니다.")
                        .asRuntimeException());
                return;
            }

            // 스케줄 조회 시, 해당 사용자의 선택 상태를 Valkey에 24시간 TTL로 저장
            try {
                redisTemplate.opsForValue().set("user:" + userId + ":selected", "true", 24, TimeUnit.HOURS);
                log.info("사용자 '{}'의 스케줄 선택 상태를 저장했습니다.", userId);
            } catch (Exception e) {
                log.error("Valkey에 스케줄 선택 상태 저장 중 오류 발생", e);
                // Valkey 오류가 핵심 기능에 영향을 주지 않도록 에러를 던지지 않고 로그만 남깁니다.
            }

            GetScheduleDetailResponse.ScheduleDetail detail = scheduleMapper.toDetail(schedule);
            GetScheduleDetailResponse response = GetScheduleDetailResponse.newBuilder()
                    .setSchedule(detail)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("스케줄 상세 정보 조회 중 오류가 발생했습니다: " + e.getMessage())
                    .asRuntimeException());
        }
    }





    @Override
    @Transactional
    public void deleteSchedule(com.mapzip.schedule.grpc.DeleteScheduleRequest request, StreamObserver<com.mapzip.schedule.grpc.DeleteScheduleResponse> responseObserver) {
        try {
            Schedule schedule = scheduleRepository.findById(request.getScheduleId())
                    .orElseThrow(() -> Status.NOT_FOUND.withDescription("스케줄을 찾을 수 없습니다: " + request.getScheduleId()).asRuntimeException());

            String userId = GrpcInterceptorConfig.USER_ID_CONTEXT_KEY.get();
            if (!schedule.getUserId().equals(userId)) {
                throw Status.PERMISSION_DENIED.withDescription("이 스케줄을 삭제할 권한이 없습니다.").asRuntimeException();
            }

            scheduleRepository.delete(schedule);

            com.mapzip.schedule.grpc.DeleteScheduleResponse response = com.mapzip.schedule.grpc.DeleteScheduleResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("스케줄이 성공적으로 삭제되었습니다.")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("스케줄 삭제 중 오류 발생", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("스케줄 삭제 중 오류 발생: " + e.getMessage())
                    .withCause(e)
                    .asRuntimeException());
        }
    }



}