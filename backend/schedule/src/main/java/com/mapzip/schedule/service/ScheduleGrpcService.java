package com.mapzip.schedule.service;

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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

import com.google.gson.Gson;
import com.mapzip.schedule.client.TmapClient;
import com.mapzip.schedule.dto.TmapRouteRequest;
import com.mapzip.schedule.dto.TmapRouteResponse;
import com.mapzip.schedule.entity.MealTimeSlot;
import com.mapzip.schedule.entity.Schedule;
import com.mapzip.schedule.entity.SelectedRestaurant;
import com.mapzip.schedule.mapper.ScheduleMapper;
import com.mapzip.schedule.repository.MealTimeSlotRepository;
import com.mapzip.schedule.repository.ScheduleRepository;
import com.mapzip.schedule.repository.SelectedRestaurantRepository;
import com.mapzip.schedule.util.TimeUtil;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
@RequiredArgsConstructor
public class ScheduleGrpcService extends ScheduleServiceGrpc.ScheduleServiceImplBase {

    private final ScheduleRepository scheduleRepository;
    private final MealTimeSlotRepository mealTimeSlotRepository;
    private final SelectedRestaurantRepository selectedRestaurantRepository;
    private final ScheduleMapper scheduleMapper;
    private final TmapClient tmapClient;
    private final RouteService routeService;
    private final Gson gson = new Gson();

    @Override
    @Transactional
    public void createSchedule(CreateScheduleRequest request, StreamObserver<CreateScheduleResponse> responseObserver) {
        try {
            // 1. 사용자의 출발 시간을 미래 시간으로 변환 (과거일 경우 다음 날로)
            // 위치는 사용자의 요청을 그대로 사용합니다.
            LocalDateTime departureDateTime = TimeUtil.parseKoreanAmPmToFuture(request.getDepartureTime());

            // 2. 스케줄 엔티티 생성 및 저장 (사용자 요청 원본 그대로 저장)
            Schedule schedule = scheduleMapper.toEntity(request);
            scheduleRepository.save(schedule);

            List<MealTimeSlot> mealTimeSlotEntities = new ArrayList<>();
            for (com.mapzip.schedule.grpc.MealTimeSlot slotRequest : request.getMealSlotsList()) {
                MealTimeSlot mealTimeSlot = new MealTimeSlot();
                mealTimeSlot.setId(slotRequest.getSlotId());
                mealTimeSlot.setSchedule(schedule);
                mealTimeSlot.setMealType(slotRequest.getMealType().getNumber());
                mealTimeSlot.setScheduledTime(slotRequest.getScheduledTime());
                mealTimeSlot.setRadius(slotRequest.getRadius());
                mealTimeSlotEntities.add(mealTimeSlot);
            }
            mealTimeSlotRepository.saveAll(mealTimeSlotEntities);

            // 3. Tmap API 호출
            TmapRouteRequest tmapRequest = scheduleMapper.toTmapRequest(request, departureDateTime);
            TmapRouteResponse tmapResponse = tmapClient.getRoutePrediction(tmapRequest);

            // 4. 경로 정보 기반으로 식사/간식 시간별 예상 위치 계산
            List<RouteService.CalculatedLocation> calculatedLocations = routeService.calculateMealLocations(
                    tmapResponse,
                    request.getMealSlotsList(),
                    departureDateTime
            );

            // 5. 계산된 위치 정보를 MealTimeSlot에 업데이트
            Map<String, MealTimeSlot> slotMap = mealTimeSlotEntities.stream()
                    .collect(Collectors.toMap(MealTimeSlot::getId, Function.identity()));

            for (RouteService.CalculatedLocation loc : calculatedLocations) {
                MealTimeSlot slot = slotMap.get(loc.getSlotId());
                if (slot != null) {
                    Map<String, Object> locationJson = new HashMap<>();
                    locationJson.put("lat", loc.getLat());
                    locationJson.put("lon", loc.getLon());
                    locationJson.put("scheduled_time", slot.getScheduledTime());
                    slot.setCalculatedLocation(gson.toJson(locationJson));
                }
            }
            mealTimeSlotRepository.saveAll(mealTimeSlotEntities);

            // 6. 계산된 도착 시간을 Schedule에 업데이트
            String arrivalTimeISO = tmapResponse.getFeatures().get(0).getProperties().getArrivalTime();
            LocalDateTime arrivalDateTime = LocalDateTime.parse(arrivalTimeISO, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            schedule.setCalculatedArrivalTime(TimeUtil.toKoreanAmPm(arrivalDateTime));
            scheduleRepository.save(schedule);

            // 7. 최종 응답 생성
            CreateScheduleResponse response = CreateScheduleResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("스케줄이 성공적으로 생성되었으며, 경로 계산이 완료되었습니다.")
                    .setScheduleId(schedule.getId())
                    .setCalculatedArrivalTime(TimeUtil.toKoreanAmPm(arrivalDateTime))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("스케줄 생성 중 오류 발생: " + e.getMessage())
                    .withCause(e)
                    .asRuntimeException());
        }
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
            SelectedRestaurant selectedRestaurant = mealTimeSlot.getSelectedRestaurant();
            if (selectedRestaurant == null) {
                selectedRestaurant = new SelectedRestaurant();
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

