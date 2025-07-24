package com.mapzip.schedule.mapper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mapzip.schedule.entity.Schedule;
import com.mapzip.schedule.grpc.*;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * gRPC 메시지와 JPA 엔티티 간의 변환을 담당하는 매퍼 클래스입니다.
 */
@Component
public class ScheduleMapper {

    private final Gson gson = new Gson();

    public Schedule toEntity(CreateScheduleRequest request) {
        Schedule schedule = new Schedule();
        schedule.setId(UUID.randomUUID().toString());
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
        Location destination = gson.fromJson(schedule.getDestinationLocation(), Location.class);
        String destinationName = (destination != null) ? destination.getName() : "";

        return GetScheduleListResponse.ScheduleSummary.newBuilder()
                .setScheduleId(schedule.getId())
                .setTitle(schedule.getTitle())
                .setDepartureTime(schedule.getDepartureTime())
                .setDestinationName(destinationName)
                .setTotalMealSlots(0) // 임시값
                .setSelectedRestaurantsCount(0) // 임시값
                .build();
    }

    /**
     * Schedule 엔티티를 ScheduleDetail gRPC 메시지로 변환합니다.
     *
     * @param schedule Schedule 엔티티
     * @return 변환된 ScheduleDetail gRPC 메시지
     */
    public GetScheduleDetailResponse.ScheduleDetail toDetail(Schedule schedule) {
        Type waypointListType = new TypeToken<List<Waypoint>>() {}.getType();
        Type stringListType = new TypeToken<List<String>>() {}.getType();

        Location departure = gson.fromJson(schedule.getDepartureLocation(), Location.class);
        Location destination = gson.fromJson(schedule.getDestinationLocation(), Location.class);
        List<Waypoint> waypoints = gson.fromJson(schedule.getWaypoints(), waypointListType);
        List<String> companions = gson.fromJson(schedule.getCompanions(), stringListType);

        return GetScheduleDetailResponse.ScheduleDetail.newBuilder()
                .setTitle(schedule.getTitle())
                .setDepartureTime(schedule.getDepartureTime())
                .setCalculatedArrivalTime(schedule.getCalculatedArrivalTime() != null ? schedule.getCalculatedArrivalTime() : "")
                .setDeparture(departure != null ? departure : Location.getDefaultInstance())
                .setDestination(destination != null ? destination : Location.getDefaultInstance())
                .addAllWaypoints(waypoints != null ? waypoints : Collections.emptyList())
                .setUserNote(schedule.getUserNote() != null ? schedule.getUserNote() : "")
                .setPurpose(schedule.getPurpose() != null ? schedule.getPurpose() : "")
                .addAllCompanions(companions != null ? companions : Collections.emptyList())
                .addAllMealSlots(Collections.emptyList()) // 임시
                .addAllSelectedRestaurants(Collections.emptyList()) // 임시
                .build();
    }
}
