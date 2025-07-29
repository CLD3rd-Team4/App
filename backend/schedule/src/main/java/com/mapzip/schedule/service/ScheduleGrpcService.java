package com.mapzip.schedule.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.GeneratedMessageV3;
import com.mapzip.schedule.client.KakaoClient;
import com.mapzip.schedule.client.TmapClient;
import com.mapzip.schedule.dto.TmapRouteRequest;
import com.mapzip.schedule.dto.TmapRouteResponse;
import com.mapzip.schedule.entity.MealTimeSlot;
import com.mapzip.schedule.entity.Schedule;
import com.mapzip.schedule.grpc.*;
import com.mapzip.schedule.mapper.ScheduleMapper;
import com.mapzip.schedule.repository.MealTimeSlotRepository;
import com.mapzip.schedule.repository.ScheduleRepository;
import com.mapzip.schedule.repository.SelectedRestaurantRepository;
import com.mapzip.schedule.util.TimeUtil;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ScheduleGrpcService extends ScheduleServiceGrpc.ScheduleServiceImplBase {

    private final ScheduleRepository scheduleRepository;
    private final MealTimeSlotRepository mealTimeSlotRepository;
    private final SelectedRestaurantRepository selectedRestaurantRepository;
    private final ScheduleMapper scheduleMapper;
    private final TmapClient tmapClient;
    private final KakaoClient kakaoClient;
    private final RouteService routeService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void createSchedule(CreateScheduleRequest request, StreamObserver<CreateScheduleResponse> responseObserver) {
        try {
            Schedule schedule = scheduleMapper.toEntity(request);
            executeTmapAndKakaoProcess(schedule, request); // 동기 호출

            CreateScheduleResponse response = CreateScheduleResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("스케줄이 성공적으로 생성되었습니다.")
                    .setScheduleId(schedule.getId())
                    .setCalculatedArrivalTime(schedule.getCalculatedArrivalTime())
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

            if (!schedule.getUserId().equals(request.getUserId())) {
                throw Status.PERMISSION_DENIED.withDescription("이 스케줄을 수정할 권한이 없습니다.").asRuntimeException();
            }

            scheduleMapper.updateEntity(schedule, request);

            if (schedule.getMealTimeSlots() != null && !schedule.getMealTimeSlots().isEmpty()) {
                // orphanRemoval=true 옵션에 의해 clear()만으로도 DB에서 삭제 쿼리가 나감
                schedule.clearMealTimeSlots();
            }

            executeTmapAndKakaoProcess(schedule, request); // 동기 호출

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

    private void executeTmapAndKakaoProcess(Schedule schedule, GeneratedMessageV3 request) {
        List<com.mapzip.schedule.grpc.MealTimeSlot> mealSlotsRequest;
        String departureTimeRequest;

        if (request instanceof CreateScheduleRequest) {
            CreateScheduleRequest r = (CreateScheduleRequest) request;
            mealSlotsRequest = r.getMealSlotsList();
            departureTimeRequest = r.getDepartureTime();
        } else if (request instanceof UpdateScheduleRequest) {
            UpdateScheduleRequest r = (UpdateScheduleRequest) request;
            mealSlotsRequest = r.getMealSlotsList();
            departureTimeRequest = r.getDepartureTime();
        } else {
            throw new IllegalArgumentException("지원하지 않는 요청 타입입니다.");
        }

        LocalDateTime departureDateTime = TimeUtil.parseKoreanAmPmToFuture(departureTimeRequest, java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")));
        if (departureDateTime.isBefore(TimeUtil.now())) {
            departureDateTime = departureDateTime.plusDays(1);
        }

        List<MealTimeSlot> mealTimeSlotEntities = new ArrayList<>();
        for (com.mapzip.schedule.grpc.MealTimeSlot slotRequest : mealSlotsRequest) {
            MealTimeSlot mealTimeSlot = new MealTimeSlot();
            mealTimeSlot.setId(java.util.UUID.randomUUID().toString());
            mealTimeSlot.setSchedule(schedule); // *** 양방향 관계 설정 ***
            mealTimeSlot.setMealType(slotRequest.getMealType().getNumber());
            mealTimeSlot.setScheduledTime(slotRequest.getScheduledTime());
            mealTimeSlot.setRadius(slotRequest.getRadius() > 0 ? slotRequest.getRadius() : 1000);
            mealTimeSlotEntities.add(mealTimeSlot);
        }
        schedule.getMealTimeSlots().addAll(mealTimeSlotEntities); // 기존 컬렉션에 추가
        
        // Tmap API 호출을 동기적으로 실행하고 결과를 기다림
        TmapRouteRequest tmapRequest = scheduleMapper.toTmapRequest(request, departureDateTime);
        TmapRouteResponse tmapResponse = tmapClient.getRoutePrediction(tmapRequest).block();

        if (tmapResponse == null) {
            throw new RuntimeException("Tmap API로부터 응답을 받지 못했습니다.");
        }

        List<RouteService.CalculatedLocation> calculatedLocations = routeService.calculateMealLocations(
                tmapResponse, mealTimeSlotEntities, departureDateTime
        );

        // Kakao API 호출을 동기적으로 실행하고 결과를 기다림
        List<MealTimeSlot> updatedSlots = Flux.fromIterable(calculatedLocations)
                .flatMap(loc -> {
                    MealTimeSlot slot = mealTimeSlotEntities.stream()
                            .filter(s -> s.getId().equals(loc.getSlotId()))
                            .findFirst().orElse(null);
                    if (slot == null) return Mono.empty();

                    return kakaoClient.searchRestaurants(loc.getLat(), loc.getLon(), slot.getRadius())
                            .map(kakaoResponse -> {
                                try {
                                    log.info("Found {} restaurants for slot {}", kakaoResponse.getDocuments().size(), slot.getId());
                                    Map<String, Object> locationJson = new HashMap<>();
                                    locationJson.put("lat", loc.getLat());
                                    locationJson.put("lon", loc.getLon());
                                    locationJson.put("scheduled_time", slot.getScheduledTime());
                                    slot.setCalculatedLocation(objectMapper.writeValueAsString(locationJson));
                                    return slot;
                                } catch (Exception e) {
                                    throw new RuntimeException("Kakao API 응답 처리 중 오류 발생", e);
                                }
                            });
                })
                .collectList()
                .block();

        int totalTimeInSeconds = tmapResponse.getFeatures().get(0).getProperties().getTotalTime();
        LocalDateTime arrivalDateTime = departureDateTime.plusSeconds(totalTimeInSeconds);
        schedule.setCalculatedArrivalTime(TimeUtil.toKoreanAmPm(arrivalDateTime));

        // 모든 변경사항을 포함하여 schedule을 최종 저장
        scheduleRepository.save(schedule);
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
                    .orElseThrow(() -> Status.NOT_FOUND.withDescription("스케줄을 찾을 수 없습니다: " + request.getScheduleId()).asRuntimeException());

            if (!schedule.getUserId().equals(request.getUserId())) {
                throw Status.PERMISSION_DENIED.withDescription("이 스케줄에 접근할 권한이 없습니다.").asRuntimeException();
            }

            MealTimeSlot mealTimeSlot = mealTimeSlotRepository.findById(request.getSlotId())
                    .orElseThrow(() -> Status.NOT_FOUND.withDescription("시간 슬롯을 찾을 수 없습니다: " + request.getSlotId()).asRuntimeException());

            com.mapzip.schedule.entity.SelectedRestaurant selectedRestaurant = mealTimeSlot.getSelectedRestaurant();
            if (selectedRestaurant == null) {
                selectedRestaurant = new com.mapzip.schedule.entity.SelectedRestaurant();
                selectedRestaurant.setMealTimeSlot(mealTimeSlot);
                selectedRestaurant.setSchedule(schedule);
                mealTimeSlot.setSelectedRestaurant(selectedRestaurant);
            }

            selectedRestaurant.setRestaurantId(request.getRestaurantId());
            selectedRestaurant.setName(request.getName());
            selectedRestaurant.setDetailUrl(request.getDetailUrl());
            selectedRestaurant.setScheduledTime(mealTimeSlot.getScheduledTime());
            selectedRestaurant.setSelectedAt(java.time.LocalDateTime.now());

            mealTimeSlotRepository.save(mealTimeSlot);

            SelectRestaurantResponse response = SelectRestaurantResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("맛집이 성공적으로 선택되었습니다.")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("맛집 선택 중 오류 발생: " + e.getMessage())
                    .withCause(e)
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


