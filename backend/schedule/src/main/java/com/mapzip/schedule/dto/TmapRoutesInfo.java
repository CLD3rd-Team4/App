package com.mapzip.schedule.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TmapRoutesInfo {
    private TmapLocation departure;
    private TmapLocation destination;
    
    @JsonProperty("wayPoints")  // JSON에서 wayPoints(대문자 P)로 직렬화
    private WaypointsContainer waypoints;
    
    private String predictionType;
    private String predictionTime;
    private String searchOption;
    private String tollgateCarType;
}
