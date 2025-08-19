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
    private String runId;

    // 도착 여유 시간
    private Integer arrivalBufferMinutes;

    // 추천 업데이트 컨텍스트
    private RecommendUpdateContext recommendUpdateContext;

    @Data
    public static class LocationDto {
        private String lat;
        private String lng;
        private String name;
    }

    @Data
    public static class RecommendUpdateContext {
        private String clientNowIso;  // 현재 시간
        private Double currentLat;    // 현재 위도
        private Double currentLng;    // 현재 경도
        private Boolean isUpdate;     // true면 "추천 업데이트 요청"
    }
}
