package com.mapzip.schedule.mapper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mapzip.schedule.dto.*;
import com.mapzip.schedule.entity.MealTimeSlot;
import com.mapzip.schedule.entity.Schedule;
import com.mapzip.schedule.entity.SelectedRestaurant;
import com.mapzip.schedule.grpc.CreateScheduleRequest;
import com.mapzip.schedule.grpc.GetScheduleDetailResponse;
import com.mapzip.schedule.grpc.GetScheduleListResponse;
import com.mapzip.schedule.repository.MealTimeSlotRepository;
import com.mapzip.schedule.repository.SelectedRestaurantRepository;
import com.mapzip.schedule.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ScheduleMapper {

    private final MealTimeSlotRepository mealTimeSlotRepository;
    private final SelectedRestaurantRepository selectedRestaurantRepository;
    private final Gson gson = new Gson();
    private static final Type WAYPOINT_LIST_TYPE = new TypeToken<List<com.mapzip.schedule.grpc.Waypoint>>() {}.getType();
    private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() {}.getType();

    public Schedule toEntity(CreateScheduleRequest request) {
        Schedule schedule = new Schedule();
        schedule.setId(java.util.UUID.randomUUID().toString());
        schedule.setUserId(request.getUserId());
        schedule.setTitle(request.getTitle());
        schedule.setDepartureTime(request.getDepartureTime());
        schedule.setUserNote(request.getUserNote());
        schedule.setPurpose(request.getPurpose());

        schedule.setDepartureLocation(gson.toJson(request.getDeparture()));
        schedule.setDestinationLocation(gson.toJson(request.getDestination()));
        schedule.setWaypoints(gson.toJson(request.getWaypointsList()));
        schedule.setCompanions(gson.toJson(request.getCompanionsList()));

        return schedule;
    }

    public GetScheduleListResponse.ScheduleSummary toSummary(Schedule schedule) {
        com.mapzip.schedule.grpc.Location destination = gson.fromJson(schedule.getDestinationLocation(), com.mapzip.schedule.grpc.Location.class);
        int totalMealSlots = mealTimeSlotRepository.countBySchedule(schedule);
        int selectedRestaurantsCount = selectedRestaurantRepository.countBySchedule(schedule);

        return GetScheduleListResponse.ScheduleSummary.newBuilder()
                .setScheduleId(schedule.getId())
                .setTitle(schedule.getTitle())
                .setDepartureTime(schedule.getDepartureTime())
                .setDestinationName(destination.getName())
                .setTotalMealSlots(totalMealSlots)
                .setSelectedRestaurantsCount(selectedRestaurantsCount)
                .build();
    }

    public GetScheduleDetailResponse.ScheduleDetail toDetail(Schedule schedule) {
        com.mapzip.schedule.grpc.Location departure = gson.fromJson(schedule.getDepartureLocation(), com.mapzip.schedule.grpc.Location.class);
        com.mapzip.schedule.grpc.Location destination = gson.fromJson(schedule.getDestinationLocation(), com.mapzip.schedule.grpc.Location.class);
        List<com.mapzip.schedule.grpc.Waypoint> waypoints = gson.fromJson(schedule.getWaypoints(), WAYPOINT_LIST_TYPE);
        List<String> companions = gson.fromJson(schedule.getCompanions(), STRING_LIST_TYPE);

        List<MealTimeSlot> mealTimeSlotEntities = mealTimeSlotRepository.findBySchedule(schedule);
        List<com.mapzip.schedule.grpc.MealTimeSlot> mealTimeSlots = mealTimeSlotEntities.stream()
                .map(this::toGrpcMealTimeSlot)
                .collect(Collectors.toList());

        List<SelectedRestaurant> selectedRestaurantEntities = selectedRestaurantRepository.findBySchedule(schedule);
        List<com.mapzip.schedule.grpc.SelectedRestaurant> selectedRestaurants = selectedRestaurantEntities.stream()
                .map(this::toGrpcSelectedRestaurant)
                .collect(Collectors.toList());

        GetScheduleDetailResponse.ScheduleDetail.Builder builder = GetScheduleDetailResponse.ScheduleDetail.newBuilder()
                .setTitle(schedule.getTitle())
                .setDepartureTime(schedule.getDepartureTime())
                .setCalculatedArrivalTime(schedule.getCalculatedArrivalTime() != null ? schedule.getCalculatedArrivalTime() : "")
                .setDeparture(departure)
                .setDestination(destination)
                .addAllWaypoints(waypoints != null ? waypoints : Collections.emptyList())
                .addAllMealSlots(mealTimeSlots)
                .addAllSelectedRestaurants(selectedRestaurants)
                .setUserNote(schedule.getUserNote() != null ? schedule.getUserNote() : "")
                .setPurpose(schedule.getPurpose() != null ? schedule.getPurpose() : "");

        if (companions != null) {
            builder.addAllCompanions(companions);
        }

        return builder.build();
    }

    public com.mapzip.schedule.grpc.MealTimeSlot toGrpcMealTimeSlot(MealTimeSlot entity) {
        return com.mapzip.schedule.grpc.MealTimeSlot.newBuilder()
                .setSlotId(entity.getId())
                .setMealType(com.mapzip.schedule.grpc.MealType.forNumber(entity.getMealType()))
                .setScheduledTime(entity.getScheduledTime())
                .setRadius(entity.getRadius())
                .build();
    }

    public com.mapzip.schedule.grpc.SelectedRestaurant toGrpcSelectedRestaurant(SelectedRestaurant entity) {
        return com.mapzip.schedule.grpc.SelectedRestaurant.newBuilder()
                .setSlotId(entity.getSlotId())
                .setRestaurantId(entity.getRestaurantId())
                .setName(entity.getName())
                .setScheduledTime(entity.getScheduledTime())
                .setDetailUrl(entity.getDetailUrl() != null ? entity.getDetailUrl() : "")
                .build();
    }

    public TmapRouteRequest toTmapRequest(CreateScheduleRequest grpcRequest, LocalDateTime departureDateTime) {
        String tmapDepartureTime = TimeUtil.toTmapApiFormat(departureDateTime);

        TmapLocation departure = new TmapLocation(
                grpcRequest.getDeparture().getName(),
                String.valueOf(grpcRequest.getDeparture().getLng()),
                String.valueOf(grpcRequest.getDeparture().getLat())
        );

        TmapLocation destination = new TmapLocation(
                grpcRequest.getDestination().getName(),
                String.valueOf(grpcRequest.getDestination().getLng()),
                String.valueOf(grpcRequest.getDestination().getLat())
        );

        List<TmapWaypoint> waypoints = grpcRequest.getWaypointsList().stream()
                .map(wp -> new TmapWaypoint(String.valueOf(wp.getLng()), String.valueOf(wp.getLat())))
                .collect(Collectors.toList());

        WaypointsContainer waypointsContainer = new WaypointsContainer(waypoints);

        TmapRoutesInfo routesInfo = new TmapRoutesInfo(departure, destination, waypointsContainer, "departure", tmapDepartureTime, "00");
        return new TmapRouteRequest(routesInfo);
    }
}


