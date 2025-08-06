package com.mapzip.recommend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapzip.schedule.dto.MealSlotData;
import com.mapzip.schedule.entity.MealTimeSlot;
import com.mapzip.schedule.entity.Schedule;
import com.mapzip.schedule.grpc.*;
import com.mapzip.schedule.mapper.ScheduleMapper;
import com.mapzip.schedule.repository.MealTimeSlotRepository;
import com.mapzip.schedule.repository.ScheduleRepository;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ScheduleGrpcService extends ScheduleServiceGrpc.ScheduleServiceImplBase {

    private final ScheduleRepository scheduleRepository;
    private final MealTimeSlotRepository mealTimeSlotRepository;
    
    private final ScheduleMapper scheduleMapper;
    private final ObjectMapper objectMapper;
    private final TmapCalculationProcessor tmapCalculationProcessor;

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
    public void processSchedule(ProcessScheduleRequest request, StreamObserver<ProcessScheduleResponse> responseObserver) {
        try {
            Schedule schedule = scheduleRepository.findById(request.getScheduleId())
                    .orElseThrow(() -> Status.NOT_FOUND.withDescription("스케줄을 찾을 수 없습니다: " + request.getScheduleId()).asRuntimeException());

            schedule.setCalculatedArrivalTime(null);

            Map<String, Object> jobData = new HashMap<>();
            jobData.put("scheduleId", schedule.getId());
            jobData.put("userId", schedule.getUserId());
            jobData.put("type", request.getType().toString());
            jobData.put("departureTime", schedule.getDepartureTime());

            TypeReference<Map<String, Object>> mapTypeRef = new TypeReference<>() {};
            TypeReference<List<Map<String, Object>>> listMapTypeRef = new TypeReference<>() {};
            jobData.put("departure", objectMapper.readValue(schedule.getDepartureLocation(), mapTypeRef));
            jobData.put("destination", objectMapper.readValue(schedule.getDestinationLocation(), mapTypeRef));
            jobData.put("waypoints", objectMapper.readValue(schedule.getWaypoints(), listMapTypeRef));

            List<MealSlotData> mealSlotDataList = schedule.getMealTimeSlots().stream()
                    .map(slot -> new MealSlotData(slot.getId(), slot.getMealType(), slot.getScheduledTime(), slot.getRadius()))
                    .collect(Collectors.toList());
            jobData.put("mealSlots", mealSlotDataList);
            jobData.put("createdAt", LocalDateTime.now().toString());

            if (request.getType() == ProcessType.UPDATE) {
                jobData.put("currentLat", request.getCurrentLat());
                jobData.put("currentLng", request.getCurrentLng());
                jobData.put("currentTime", request.getCurrentTime());
            }

            Schedule updatedSchedule = tmapCalculationProcessor.calculateAndSave(jobData);

            GetScheduleDetailResponse.ScheduleDetail scheduleDetail = scheduleMapper.toDetail(updatedSchedule);

            ProcessScheduleResponse response = ProcessScheduleResponse.newBuilder()
                    .setSchedule(scheduleDetail)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("스케줄 처리 요청 중 오류 발생", e);
            responseObserver.onError(Status.INTERNAL.withDescription("스케줄 처리 요청 중 오류: " + e.getMessage()).withCause(e).asRuntimeException());
        }
    }

    @Override
    @Transactional
    public void updateSchedule(UpdateScheduleRequest request, StreamObserver<GetScheduleDetailResponse> responseObserver) {
        try {
            Schedule schedule = scheduleRepository.findById(request.getScheduleId())
                    .orElseThrow(() -> Status.NOT_FOUND.withDescription("수정할 스케줄을 찾을 수 없습니다: " + request.getScheduleId()).asRuntimeException());

            if (!schedule.getUserId().equals(request.getUserId())) {
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
            Schedule schedule = scheduleRepository.findById(request.getScheduleId())
                    .orElseThrow(() -> new IllegalArgumentException("스케줄을 찾을 수 없습니다."));

            if (!schedule.getUserId().equals(request.getUserId())) {
                responseObserver.onError(io.grpc.Status.PERMISSION_DENIED
                        .withDescription("해당 스케줄에 접근할 권한이 없습니다.")
                        .asRuntimeException());
                return;
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
    public void refreshSchedule(RefreshScheduleRequest request, StreamObserver<RefreshScheduleResponse> responseObserver) {
        responseObserver.onError(Status.UNIMPLEMENTED.withDescription("Method not implemented").asRuntimeException());
    }

    @Override
    @Transactional
    public void deleteSchedule(com.mapzip.schedule.grpc.DeleteScheduleRequest request, StreamObserver<com.mapzip.schedule.grpc.DeleteScheduleResponse> responseObserver) {
        try {
            Schedule schedule = scheduleRepository.findById(request.getScheduleId())
                    .orElseThrow(() -> Status.NOT_FOUND.withDescription("스케줄을 찾을 수 없습니다: " + request.getScheduleId()).asRuntimeException());

            if (!schedule.getUserId().equals(request.getUserId())) {
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