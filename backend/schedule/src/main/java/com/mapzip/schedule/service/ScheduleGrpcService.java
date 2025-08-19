package com.mapzip.schedule.service;

import com.mapzip.schedule.config.GrpcInterceptorConfig;

import com.mapzip.schedule.entity.MealTimeSlot;
import com.mapzip.schedule.entity.Schedule;
import com.mapzip.schedule.grpc.*;
import com.mapzip.schedule.mapper.ScheduleMapper;
import com.mapzip.schedule.repository.MealTimeSlotRepository;
import com.mapzip.schedule.repository.ScheduleRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.persistence.LockModeType;

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
    
    private final RedisTemplate<String, String> redisTemplate;

    // @GrpcClient("recommend-service")
    // private RouteCalculatorServiceGrpc.RouteCalculatorServiceBlockingStub recommendClient;

    @Override
    @Transactional
    public void createSchedule(CreateScheduleRequest request, StreamObserver<CreateScheduleResponse> responseObserver) {
        try {
            com.google.protobuf.util.JsonFormat.Printer printer = com.google.protobuf.util.JsonFormat.printer().includingDefaultValueFields().preservingProtoFieldNames();
            log.info("CreateSchedule Request Received (JSON):\n{}", printer.print(request));
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            log.warn("Failed to serialize request to JSON for logging", e);
            log.info("CreateSchedule Request Received (toString): {}", request.toString());
        }
        try {
            String userId = GrpcInterceptorConfig.USER_ID_CONTEXT_KEY.get();
            Schedule schedule = scheduleMapper.toEntity(request);
            schedule.setUserId(userId);

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
            log.info("[DEBUG] updateSchedule RPC called with scheduleId: {}", request.getScheduleId());
            try {
            Schedule schedule = scheduleRepository.findById(request.getScheduleId(),LockModeType.PESSIMISTIC_WRITE)
                    .orElseThrow(() -> Status.NOT_FOUND.withDescription("수정할 스케줄을 찾을 수 없습니다: " + request.getScheduleId()).asRuntimeException());

            String userId = GrpcInterceptorConfig.USER_ID_CONTEXT_KEY.get();
            if (!schedule.getUserId().equals(userId)) {
                throw Status.PERMISSION_DENIED.withDescription("이 스케줄을 수정할 권한이 없습니다.").asRuntimeException();
            }

            scheduleMapper.updateEntity(schedule, request);

            // 기존 MealTimeSlot을 명시적으로 삭제
            if (schedule.getMealTimeSlots() != null && !schedule.getMealTimeSlots().isEmpty()) {
                mealTimeSlotRepository.deleteAll(schedule.getMealTimeSlots());
                schedule.getMealTimeSlots().clear();
            }

            // 요청으로부터 새로운 MealTimeSlot 생성 및 추가
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
            String userId = GrpcInterceptorConfig.USER_ID_CONTEXT_KEY.get();
            List<Schedule> schedules = scheduleRepository.findByUserIdOrderByCreatedAtDesc(userId);
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
            // Call the internal helper for pure detail retrieval
            GetScheduleDetailResponse response = getScheduleDetailInternal(request.getScheduleId());
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
    public void selectSchedule(SelectScheduleRequest request, StreamObserver<GetScheduleDetailResponse> responseObserver) {
        try {
            String scheduleId = request.getScheduleId();
            String userId = GrpcInterceptorConfig.USER_ID_CONTEXT_KEY.get();

            if (userId == null || userId.isEmpty()) {
                responseObserver.onError(io.grpc.Status.UNAUTHENTICATED
                        .withDescription("사용자 ID를 확인할 수 없습니다.")
                        .asRuntimeException());
                return;
            }

            // Save selection state to Valkey
            try {
                String selectionKey = "user:" + userId + ":selectedSchedule";
                redisTemplate.opsForValue().set(selectionKey, scheduleId, 24, TimeUnit.HOURS);
                log.info("사용자 '{}'의 선택된 스케줄 ID '{}'를 저장했습니다.", userId, scheduleId);

                // 기존의 boolean 타입 키는 삭제합니다.
                redisTemplate.delete("user:" + userId + ":selected");

            } catch (Exception e) {
                log.error("Valkey에 스케줄 선택 상태 저장 중 오류 발생", e);
                // Valkey 오류가 핵심 기능에 영향을 주지 않도록 에러를 던지지 않고 로그만 남깁니다.
            }

            // Call the internal helper for detail retrieval
            GetScheduleDetailResponse response = getScheduleDetailInternal(scheduleId);
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

    // Private helper method for pure schedule detail retrieval
    private GetScheduleDetailResponse getScheduleDetailInternal(String scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("스케줄을 찾을 수 없습니다."));

        String userId = GrpcInterceptorConfig.USER_ID_CONTEXT_KEY.get();
        if (userId == null || userId.isEmpty()) {
            throw io.grpc.Status.UNAUTHENTICATED
                    .withDescription("사용자 ID를 확인할 수 없습니다.")
                    .asRuntimeException();
        }
        
        if (!schedule.getUserId().equals(userId)) {
            throw io.grpc.Status.PERMISSION_DENIED
                    .withDescription("해당 스케줄에 접근할 권한이 없습니다.")
                    .asRuntimeException();
        }

        GetScheduleDetailResponse.ScheduleDetail detail = scheduleMapper.toDetail(schedule);
        return GetScheduleDetailResponse.newBuilder()
                .setSchedule(detail)
                .build();
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

    @Override
    @Transactional(readOnly = true)
    public void isScheduleSelected(IsScheduleSelectedRequest request, StreamObserver<IsScheduleSelectedResponse> responseObserver) {
        try {
            String userId = GrpcInterceptorConfig.USER_ID_CONTEXT_KEY.get();
            if (userId == null || userId.isEmpty()) {
                responseObserver.onError(io.grpc.Status.UNAUTHENTICATED
                        .withDescription("사용자 ID를 확인할 수 없습니다.")
                        .asRuntimeException());
                return;
            }

            String selectionKey = "user:" + userId + ":selectedSchedule";
            String scheduleId = redisTemplate.opsForValue().get(selectionKey);

            boolean isSelected = (scheduleId != null && !scheduleId.isEmpty());

            log.info("사용자 '{}'의 선택된 스케줄 ID를 조회했습니다: {}", userId, isSelected ? scheduleId : "없음");

            IsScheduleSelectedResponse.Builder responseBuilder = IsScheduleSelectedResponse.newBuilder().setIsSelected(isSelected);

            if (isSelected) {
                responseBuilder.setScheduleId(scheduleId);
            }
            
            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("스케줄 선택 상태 조회 중 오류 발생", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("스케줄 선택 상태 조회 중 오류가 발생했습니다: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    @Transactional
    public void deselectSchedule(DeselectScheduleRequest request, StreamObserver<DeselectScheduleResponse> responseObserver) {
        try {
            String userId = GrpcInterceptorConfig.USER_ID_CONTEXT_KEY.get();
            if (userId == null || userId.isEmpty()) {
                responseObserver.onError(io.grpc.Status.UNAUTHENTICATED
                        .withDescription("사용자 ID를 확인할 수 없습니다.")
                        .asRuntimeException());
                return;
            }

            String selectionKey = "user:" + userId + ":selectedSchedule";
            Boolean deleted = redisTemplate.delete(selectionKey);

            // 기존의 boolean 타입 키도 함께 삭제합니다. (마이그레이션 목적)
            redisTemplate.delete("user:" + userId + ":selected");

            if (Boolean.TRUE.equals(deleted)) {
                log.info("사용자 '{}'의 선택된 스케줄 ID를 삭제했습니다.", userId);
            } else {
                log.warn("사용자 '{}'의 선택된 스케줄 ID 키가 존재하지 않거나 삭제에 실패했습니다.", userId);
            }

            DeselectScheduleResponse response = DeselectScheduleResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("스케줄 선택이 해제되었습니다.")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("스케줄 선택 해제 중 오류 발생", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("스케줄 선택 해제 중 오류가 발생했습니다: " + e.getMessage())
                    .asRuntimeException());
        }
    }



}
