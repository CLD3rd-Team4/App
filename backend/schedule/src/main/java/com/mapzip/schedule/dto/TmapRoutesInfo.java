package com.mapzip.schedule.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TmapRoutesInfo {
    private TmapLocation departure;
    private TmapLocation destination;

    @JsonProperty("wayPoints")
    @JsonInclude(JsonInclude.Include.NON_EMPTY) // 리스트가 비어있으면 JSON에서 생략
    private WaypointsContainer wayPoints;

    private String predictionType = "departure";
    private String predictionTime;
    private String searchOption = "00";
}

