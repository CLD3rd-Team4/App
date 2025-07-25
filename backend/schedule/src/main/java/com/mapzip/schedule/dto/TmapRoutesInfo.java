package com.mapzip.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TmapRoutesInfo {
    private TmapLocation departure;
    private TmapLocation destination;
    private WaypointsContainer waypoints;
    private String predictionType;
    private String predictionTime;
    private String searchOption;
    private String tollgateCarType;
}
