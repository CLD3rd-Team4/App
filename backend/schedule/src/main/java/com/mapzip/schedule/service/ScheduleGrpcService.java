package com.mapzip.schedule.service;

import com.mapzip.schedule.entity.MealTimeSlot;
import com.mapzip.schedule.entity.Schedule;
import com.mapzip.schedule.entity.SelectedRestaurant;
import com.mapzip.schedule.grpc.*;
import com.mapzip.schedule.mapper.ScheduleMapper;
import com.mapzip.schedule.repository.MealTimeSlotRepository;
import com.mapzip.schedule.repository.ScheduleRepository;
import com.mapzip.schedule.repository.SelectedRestaurantRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import net.devh.boot.grpc.server.service.GrpcService;


import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * gRPC ScheduleService를 구현하는 서비스 클래스입니다.
 * 데이터 변환 로직은 ScheduleMapper에 위임합니다.
 */
@GrpcService
@RequiredArgsConstructor
public class ScheduleGrpcService extends ScheduleServiceGrpc.ScheduleServiceImplBase {

    private final ScheduleRepository scheduleRepository;
    private final MealTimeSlotRepository mealTimeSlotRepository;
    private final SelectedRestaurantRepository selectedRestaurantRepository;
    private final ScheduleMapper scheduleMapper;

    @Override
    @Transactional
    public void createSchedule(CreateScheduleRequest request, StreamObserver<CreateScheduleResponse> responseObserver) {
        try {
            Schedule schedule = scheduleMapper.toEntity(request);
            scheduleRepository.save(schedule);

            for (com.mapzip.schedule.grpc.MealTimeSlot slotRequest : request.getMealSlotsList()) {
                MealTimeSlot mealTimeSlot = new MealTimeSlot();
                mealTimeSlot.setId(UUID.randomUUID().toString());
                mealTimeSlot.setSchedule(schedule);
                mealTimeSlot.setMealType(slotRequest.getMealType().getNumber());
                mealTimeSlot.setScheduledTime(slotRequest.getScheduledTime());
                mealTimeSlot.setRadius(slotRequest.getRadius());
                mealTimeSlotRepository.save(mealTimeSlot);
            }

            CreateScheduleResponse response = CreateScheduleResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("스케줄이 성공적으로 생성되었습니다.")
                    .setScheduleId(schedule.getId())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("스케줄 생성 중 오류가 발생했습니다: " + e.getMessage())
                    .asRuntimeException());
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
    @Transactional
    public void selectRestaurant(SelectRestaurantRequest request, StreamObserver<SelectRestaurantResponse> responseObserver) {
        try {
            Schedule schedule = scheduleRepository.findById(request.getScheduleId())
                    .orElseThrow(() -> new IllegalArgumentException("스케줄을 찾을 수 없습니다."));

            if (!schedule.getUserId().equals(request.getUserId())) {
                responseObserver.onError(io.grpc.Status.PERMISSION_DENIED
                        .withDescription("해당 스케줄에 접근할 권한이 없습니다.")
                        .asRuntimeException());
                return;
            }

            MealTimeSlot mealTimeSlot = mealTimeSlotRepository.findById(request.getSlotId())
                    .orElseThrow(() -> new IllegalArgumentException("식사 시간 슬롯을 찾을 수 없습니다."));

            // 맛집 정보는 임시 데이터 사용
            String restaurantName = "선택된 맛집 (ID: " + request.getRestaurantId() + ")";
            String detailUrl = "https://placeholder.url/for/" + request.getRestaurantId();

            // Check if SelectedRestaurant already exists for this slotId
            SelectedRestaurant selectedRestaurant = selectedRestaurantRepository.findById(mealTimeSlot.getId())
                    .orElseGet(() -> {
                        SelectedRestaurant newSelectedRestaurant = new SelectedRestaurant();
                        newSelectedRestaurant.setSlotId(mealTimeSlot.getId()); // Explicitly set ID for new entity
                        newSelectedRestaurant.setMealTimeSlot(mealTimeSlot);
                        newSelectedRestaurant.setSchedule(schedule);
                        return newSelectedRestaurant;
                    });

            // Update fields (for both new and existing entities)
            selectedRestaurant.setRestaurantId(request.getRestaurantId());
            selectedRestaurant.setName(restaurantName);
            selectedRestaurant.setScheduledTime(mealTimeSlot.getScheduledTime());
            selectedRestaurant.setDetailUrl(detailUrl);

            selectedRestaurantRepository.save(selectedRestaurant);

            com.mapzip.schedule.grpc.SelectedRestaurant grpcSelectedRestaurant =
                    com.mapzip.schedule.grpc.SelectedRestaurant.newBuilder()
                            .setSlotId(selectedRestaurant.getSlotId())
                            .setRestaurantId(selectedRestaurant.getRestaurantId())
                            .setName(selectedRestaurant.getName())
                            .setScheduledTime(selectedRestaurant.getScheduledTime())
                            .setDetailUrl(selectedRestaurant.getDetailUrl())
                            .build();

            SelectRestaurantResponse response = SelectRestaurantResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("맛집이 성공적으로 선택되었습니다.")
                    .setSelectedRestaurant(grpcSelectedRestaurant)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("맛집 선택 중 오류가 발생했습니다: " + e.getMessage())
                    .asRuntimeException());
        }
    }
}
