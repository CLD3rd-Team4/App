package com.mapzip.schedule.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mapzip.schedule.entity.MealTimeSlot;
import com.mapzip.schedule.entity.Schedule;
import com.mapzip.schedule.entity.SelectedRestaurant;
import com.mapzip.schedule.grpc.*;
import com.mapzip.schedule.repository.MealTimeSlotRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ScheduleMapper {

    private final MealTimeSlotRepository mealTimeSlotRepository;
    
    private final Gson gson = new Gson();
    private final ObjectMapper objectMapper;
    private static final Type WAYPOINT_LIST_TYPE = new TypeToken<List<Map<String, Object>>>() {}.getType();
    private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() {}.getType();

    private Map<String, Object> waypointToMap(com.mapzip.schedule.grpc.Waypoint waypoint) {
        Map<String, Object> map = new HashMap<>();
        map.put("lat", waypoint.getLat());
        map.put("lng", waypoint.getLng());
        map.put("name", waypoint.getName());
        if (waypoint.getArrivalTime() != null && !waypoint.getArrivalTime().isEmpty()) {
            map.put("arrivalTime", waypoint.getArrivalTime());
        }
        return map;
    }

    public Schedule toEntity(CreateScheduleRequest request) throws JsonProcessingException {
        Schedule schedule = new Schedule();
        schedule.setId(java.util.UUID.randomUUID().toString());
        schedule.setUserId(request.getUserId());
        schedule.setTitle(request.getTitle());
        schedule.setDepartureTime(request.getDepartureTime());
        
        schedule.setPurpose(request.getPurpose());

        schedule.setDepartureLocation(gson.toJson(request.getDeparture()));
        schedule.setDestinationLocation(gson.toJson(request.getDestination()));

        List<Map<String, Object>> waypointMaps = request.getWaypointsList().stream()
                .map(this::waypointToMap)
                .collect(Collectors.toList());
        schedule.setWaypoints(gson.toJson(waypointMaps));
        schedule.setCompanions(gson.toJson(request.getCompanionsList()));

        return schedule;
    }

    public void updateEntity(Schedule schedule, UpdateScheduleRequest request) throws JsonProcessingException {
        schedule.setTitle(request.getTitle());
        schedule.setDepartureTime(request.getDepartureTime());
        
        schedule.setPurpose(request.getPurpose());

        schedule.setDepartureLocation(gson.toJson(request.getDeparture()));
        schedule.setDestinationLocation(gson.toJson(request.getDestination()));

        List<Map<String, Object>> waypointMaps = request.getWaypointsList().stream()
                .map(this::waypointToMap)
                .collect(Collectors.toList());
        schedule.setWaypoints(gson.toJson(waypointMaps));
        schedule.setCompanions(gson.toJson(request.getCompanionsList()));
    }

    public GetScheduleListResponse.ScheduleSummary toSummary(Schedule schedule) {
        com.mapzip.schedule.grpc.Location destination = gson.fromJson(schedule.getDestinationLocation(), com.mapzip.schedule.grpc.Location.class);
        int totalMealSlots = mealTimeSlotRepository.countBySchedule(schedule);
        

        return GetScheduleListResponse.ScheduleSummary.newBuilder()
                .setScheduleId(schedule.getId())
                .setTitle(schedule.getTitle())
                .setDepartureTime(schedule.getDepartureTime())
                .setDestinationName(destination.getName())
                .setTotalMealSlots(totalMealSlots)
                
                .build();
    }

    public GetScheduleDetailResponse.ScheduleDetail toDetail(Schedule schedule) {
        com.mapzip.schedule.grpc.Location departure = gson.fromJson(schedule.getDepartureLocation(), com.mapzip.schedule.grpc.Location.class);
        com.mapzip.schedule.grpc.Location destination = gson.fromJson(schedule.getDestinationLocation(), com.mapzip.schedule.grpc.Location.class);
        
        List<Map<String, Object>> waypointMaps = gson.fromJson(schedule.getWaypoints(), WAYPOINT_LIST_TYPE);
        List<com.mapzip.schedule.grpc.Waypoint> waypoints = new ArrayList<>();
        if (waypointMaps != null) {
            for (Map<String, Object> map : waypointMaps) {
                Waypoint.Builder waypointBuilder = Waypoint.newBuilder();
                if (map.get("lat") != null) waypointBuilder.setLat((Double) map.get("lat"));
                if (map.get("lng") != null) waypointBuilder.setLng((Double) map.get("lng"));
                if (map.get("name") != null) waypointBuilder.setName((String) map.get("name"));
                if (map.get("arrivalTime") != null) waypointBuilder.setArrivalTime((String) map.get("arrivalTime"));
                waypoints.add(waypointBuilder.build());
            }
        }

        List<String> companions = gson.fromJson(schedule.getCompanions(), STRING_LIST_TYPE);

        List<com.mapzip.schedule.grpc.MealTimeSlot> mealTimeSlots = schedule.getMealTimeSlots().stream()
                .map(this::toGrpcMealTimeSlot)
                .collect(Collectors.toList());

        

        GetScheduleDetailResponse.ScheduleDetail.Builder builder = GetScheduleDetailResponse.ScheduleDetail.newBuilder()
                .setTitle(schedule.getTitle())
                .setDepartureTime(schedule.getDepartureTime())
                .setDeparture(departure)
                .setDestination(destination)
                .addAllWaypoints(waypoints)
                .addAllMealSlots(mealTimeSlots)
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

    
}