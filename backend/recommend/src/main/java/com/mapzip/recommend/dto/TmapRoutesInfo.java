package com.mapzip.recommend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Null이 아닌 필드만 JSON에 포함
public class TmapRoutesInfo {
    private TmapLocation departure;
    private TmapLocation destination;

    @JsonProperty("wayPoints")
    private WaypointsContainer waypoints;

    private String predictionType;
    private String predictionTime;
    private String searchOption;
    private String tollgateCarType;
}
