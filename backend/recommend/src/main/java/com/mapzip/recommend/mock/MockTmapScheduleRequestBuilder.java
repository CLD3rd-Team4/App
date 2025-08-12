package com.mapzip.recommend.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.mapzip.recommend.dto.tmap.MealSlotData;
import com.mapzip.recommend.dto.tmap.TmapScheduleRequest;

public class MockTmapScheduleRequestBuilder {

    public static TmapScheduleRequest buildMock() {
        TmapScheduleRequest tmapScheduleRequest = new TmapScheduleRequest();
        tmapScheduleRequest.setScheduleId("schedule123");
        tmapScheduleRequest.setUserId("user123");
        tmapScheduleRequest.setDepartureTime("오전 09:00");

        tmapScheduleRequest.setDeparture(buildLocation("37.5665", "126.9780", "서울역"));
        tmapScheduleRequest.setDestination(buildLocation("37.5700", "126.9920", "광화문"));
        tmapScheduleRequest.setWaypoints(buildWaypoints());
        
        tmapScheduleRequest.setMealSlots(buildMealSlots());
        tmapScheduleRequest.setUserNote("서울 관광 일정");
        tmapScheduleRequest.setPurpose("관광");
        tmapScheduleRequest.setCompanions(Arrays.asList("친구", "가족"));
        tmapScheduleRequest.setArrivalBufferMinutes(15);

        return tmapScheduleRequest;
    }

    private static TmapScheduleRequest.LocationDto buildLocation(String lat, String lng, String name) {
        TmapScheduleRequest.LocationDto loc = new TmapScheduleRequest.LocationDto();
        loc.setLat(lat);
        loc.setLng(lng);
        loc.setName(name);
        return loc;
    }

    private static List<TmapScheduleRequest.LocationDto> buildWaypoints() {
    	List<TmapScheduleRequest.LocationDto> waypoints = new ArrayList<TmapScheduleRequest.LocationDto>();
        waypoints.add(buildLocation("37.5680", "126.9850", "시청"));
        return waypoints;
    }

    private static List<MealSlotData> buildMealSlots() {
        List<MealSlotData> mealSlots = new ArrayList<>();
        mealSlots.add(MealSlotData.builder()
                .slotId("slot1")
                .scheduledTime("오전 10:00")
                .radius(1000)
                .mealType(0) // MEAL
                .build());
        mealSlots.add(MealSlotData.builder()
                .slotId("slot2")
                .scheduledTime("오후 01:00")
                .radius(800)
                .mealType(1) // SNACK
                .build());
        mealSlots.add(MealSlotData.builder()
                .slotId("slot3")
                .scheduledTime("오후 01:30")
                .radius(800)
                .mealType(0) // SNACK
                .build());
        return mealSlots;
    }
}
