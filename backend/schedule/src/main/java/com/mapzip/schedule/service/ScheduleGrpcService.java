package com.mapzip.schedule.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapzip.schedule.client.KakaoClient;
import com.mapzip.schedule.client.TmapClient;
import com.mapzip.schedule.dto.TmapRouteRequest;
import com.mapzip.schedule.entity.MealTimeSlot;
import com.mapzip.schedule.entity.Schedule;
import com.mapzip.schedule.grpc.CreateScheduleRequest;
import com.mapzip.schedule.grpc.CreateScheduleResponse;
import com.mapzip.schedule.grpc.GetScheduleDetailRequest;
import com.mapzip.schedule.grpc.GetScheduleDetailResponse;
import com.mapzip.schedule.grpc.GetScheduleListRequest;
import com.mapzip.schedule.grpc.GetScheduleListResponse;
import com.mapzip.schedule.grpc.RefreshScheduleRequest;
import com.mapzip.schedule.grpc.RefreshScheduleResponse;
import com.mapzip.schedule.grpc.ScheduleServiceGrpc;
import com.mapzip.schedule.grpc.SelectRestaurantRequest;
import com.mapzip.schedule.grpc.SelectRestaurantResponse;
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
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
        // 1. 초기 데이터 설정 및 저장 (동기)
        LocalDateTime departureDateTime = TimeUtil.parseKoreanAmPmToFuture(request.getDepartureTime(), java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")));
        if (departureDateTime.isBefore(TimeUtil.now())) {
            departureDateTime = departureDateTime.plusDays(1);
        }

        Schedule schedule = scheduleMapper.toEntity(request);
        scheduleRepository.save(schedule);

        List<MealTimeSlot> mealTimeSlotEntities = new ArrayList<>();
        for (com.mapzip.schedule.grpc.MealTimeSlot slotRequest : request.getMealSlotsList()) {
            MealTimeSlot mealTimeSlot = new MealTimeSlot();
            mealTimeSlot.setId(java.util.UUID.randomUUID().toString());
            mealTimeSlot.setSchedule(schedule);
            mealTimeSlot.setMealType(slotRequest.getMealType().getNumber());
            mealTimeSlot.setScheduledTime(slotRequest.getScheduledTime());
            mealTimeSlot.setRadius(slotRequest.getRadius() > 0 ? slotRequest.getRadius() : 1000);
            mealTimeSlotEntities.add(mealTimeSlot);
        }
        mealTimeSlotRepository.saveAll(mealTimeSlotEntities);

        // 2. Tmap API 비동기 호출
        TmapRouteRequest tmapRequest = scheduleMapper.toTmapRequest(request, departureDateTime);
        final LocalDateTime finalDepartureDateTime = departureDateTime;

        tmapClient.getRoutePrediction(tmapRequest)
                .flatMap(tmapResponse -> {
                    // 3. 경로 정보 기반 위치 계산 (동기)
                    List<RouteService.CalculatedLocation> calculatedLocations = routeService.calculateMealLocations(
                            tmapResponse, mealTimeSlotEntities, finalDepartureDateTime
                    );

                    // 4. Kakao API 병렬 호출
                    return Flux.fromIterable(calculatedLocations)
                            .parallel()
                            .runOn(Schedulers.parallel())
                            .flatMap(loc -> {
                                MealTimeSlot slot = mealTimeSlotEntities.stream()
                                        .filter(s -> s.getId().equals(loc.getSlotId()))
                                        .findFirst().orElse(null);
                                if (slot == null) return Mono.empty();

                                return kakaoClient.searchRestaurants(loc.getLat(), loc.getLon(), slot.getRadius())
                                        .flatMap(kakaoResponse -> {
                                            try {
                                                // 파일 저장
                                                String fileName = String.format("kakao_response_%s_%s.json", schedule.getId(), slot.getId());
                                                objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(fileName), kakaoResponse);
                                                log.info("Kakao API response saved to {}", fileName);

                                                // 위치 정보 업데이트
                                                Map<String, Object> locationJson = new HashMap<>();
                                                locationJson.put("lat", loc.getLat());
                                                locationJson.put("lon", loc.getLon());
                                                locationJson.put("scheduled_time", slot.getScheduledTime());
                                                slot.setCalculatedLocation(objectMapper.writeValueAsString(locationJson));
                                                return Mono.just(slot);
                                            } catch (Exception e) {
                                                log.error("Error during Kakao API processing for slot {}: {}", slot.getId(), e.getMessage(), e);
                                                return Mono.error(e);
                                            }
                                        });
                            })
                            .sequential()
                            .collectList()
                            .flatMap(updatedSlots -> {
                                // 5. 모든 정보 취합 및 최종 저장
                                mealTimeSlotRepository.saveAll(updatedSlots);

                                int totalTimeInSeconds = tmapResponse.getFeatures().get(0).getProperties().getTotalTime();
                                LocalDateTime arrivalDateTime = finalDepartureDateTime.plusSeconds(totalTimeInSeconds);
                                schedule.setCalculatedArrivalTime(TimeUtil.toKoreanAmPm(arrivalDateTime));
                                scheduleRepository.save(schedule);

                                return Mono.just(CreateScheduleResponse.newBuilder()
                                        .setSuccess(true)
                                        .setMessage("스케줄이 성공적으로 생성되었으며, 주변 음식점 검색이 시작되었습니다.")
                                        .setScheduleId(schedule.getId())
                                        .setCalculatedArrivalTime(TimeUtil.toKoreanAmPm(arrivalDateTime))
                                        .build());
                            });
                })
                .subscribe(
                        responseObserver::onNext,
                        error -> {
                            log.error("Error creating schedule asynchronously", error);
                            responseObserver.onError(Status.INTERNAL
                                    .withDescription("스케줄 생성 중 오류 발생: " + error.getMessage())
                                    .withCause(error)
                                    .asRuntimeException());
                        },
                        responseObserver::onCompleted
                );
    }

    // ... (getScheduleList, getScheduleDetail, selectRestaurant, refreshSchedule 등 다른 메서드들은 여기에 유지)
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
            // 1. 스케줄과 시간 슬롯의 존재 여부 및 사용자 권한 확인
            Schedule schedule = scheduleRepository.findById(request.getScheduleId())
                    .orElseThrow(() -> Status.NOT_FOUND.withDescription("스케줄을 찾을 수 없습니다: " + request.getScheduleId()).asRuntimeException());

            if (!schedule.getUserId().equals(request.getUserId())) {
                throw Status.PERMISSION_DENIED.withDescription("이 스케줄에 접근할 권한이 없습니다.").asRuntimeException();
            }

            MealTimeSlot mealTimeSlot = mealTimeSlotRepository.findById(request.getSlotId())
                    .orElseThrow(() -> Status.NOT_FOUND.withDescription("시간 슬롯을 찾을 수 없습니다: " + request.getSlotId()).asRuntimeException());

            // 2. 기존에 선택된 맛집이 있는지 확인하고, 없다면 새로 생성
            com.mapzip.schedule.entity.SelectedRestaurant selectedRestaurant = mealTimeSlot.getSelectedRestaurant();
            if (selectedRestaurant == null) {
                selectedRestaurant = new com.mapzip.schedule.entity.SelectedRestaurant();
                selectedRestaurant.setMealTimeSlot(mealTimeSlot); // 관계 설정
                selectedRestaurant.setSchedule(schedule); // 관계 설정
                mealTimeSlot.setSelectedRestaurant(selectedRestaurant); // 양방향 관계 설정
            }

            // 3. 맛집 정보 업데이트 (생성 또는 수정)
            selectedRestaurant.setRestaurantId(request.getRestaurantId());
            selectedRestaurant.setName(request.getName());
            selectedRestaurant.setDetailUrl(request.getDetailUrl());
            selectedRestaurant.setScheduledTime(mealTimeSlot.getScheduledTime());
            selectedRestaurant.setSelectedAt(java.time.LocalDateTime.now()); // 선택 시간을 현재로 갱신

            // 4. mealTimeSlot을 저장하면 selectedRestaurant도 함께 저장됨 (CascadeType.ALL)
            mealTimeSlotRepository.save(mealTimeSlot);

            // 5. 성공 응답 전송
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
        // TODO: Implement refresh logic
        responseObserver.onError(Status.UNIMPLEMENTED.withDescription("Method not implemented").asRuntimeException());
    }
}

