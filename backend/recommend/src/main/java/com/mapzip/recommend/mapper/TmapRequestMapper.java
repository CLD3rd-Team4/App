package com.mapzip.recommend.mapper;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.mapzip.recommend.dto.tmap.MealSlotData;
import com.mapzip.recommend.dto.tmap.TmapScheduleRequest;
import com.mapzip.recommend.dto.tmap.TmapScheduleRequest.LocationDto;
import com.mapzip.schedule.grpc.GetScheduleDetailResponse;
import com.mapzip.schedule.grpc.MealTimeSlot;



public class TmapRequestMapper {
	
    public static TmapScheduleRequest fromScheduleDetail(GetScheduleDetailResponse.ScheduleDetail detail, String scheduleId, String userId) {
        TmapScheduleRequest request = new TmapScheduleRequest();
        request.setScheduleId(scheduleId);
        request.setUserId(userId);
        request.setDepartureTime(detail.getDepartureTime());

        // 출발지 (double → String 변환)
        LocationDto departure = convertLocation(detail.getDeparture().getLat(), detail.getDeparture().getLng(), detail.getDeparture().getName());
        request.setDeparture(departure);

        // 도착지 (double → String 변환)
        LocationDto destination = convertLocation(detail.getDestination().getLat(), detail.getDestination().getLng(), detail.getDestination().getName());
        request.setDestination(destination);

        // 경유지 (double → String 변환)
        List<LocationDto> waypoints = detail.getWaypointsList().stream()
                .map(wp -> convertLocation(wp.getLat(), wp.getLng(), wp.getName()))
                .collect(Collectors.toList());
        request.setWaypoints(waypoints);

        // 식사 슬롯
        List<MealSlotData> mealSlots = detail.getMealSlotsList().stream()
            .map((MealTimeSlot slot) -> MealSlotData.builder()
                .slotId(slot.getSlotId())
                .scheduledTime(slot.getScheduledTime())
                .radius(slot.getRadius())
                .mealType(slot.getMealType().getNumber()) // enum → int
                .build()
            )
            .collect(Collectors.toList());
        request.setMealSlots(mealSlots);

        // 기타
        request.setUserNote(detail.getUserNote());
        request.setPurpose(detail.getPurpose());
        request.setCompanions(detail.getCompanionsList());


        return request;
    }


    private static LocationDto convertLocation(double lat, double lng, String name) {
        LocationDto dto = new LocationDto();
        dto.setLat(String.valueOf(lat));
        dto.setLng(String.valueOf(lng));
        dto.setName(name);
        return dto;
    }
}

