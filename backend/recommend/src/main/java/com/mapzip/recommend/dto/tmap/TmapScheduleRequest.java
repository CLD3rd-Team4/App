package com.mapzip.recommend.dto.tmap;


import lombok.Data;
import java.util.List;

@Data
public class TmapScheduleRequest {
    private String scheduleId;
    private String userId;
    private String departureTime;

    private LocationDto departure;
    private LocationDto destination;
    private List<LocationDto> waypoints;

    private List<MealSlotData> mealSlots;


    private String userNote;
    private String purpose;
    private List<String> companions;
    @Data
    public static class LocationDto {
        private String lat;
        private String lng;
        private String name;
    }
    //도착 여유 시간 
    private Integer arrivalBufferMinutes;
}
