package com.mapzip.schedule.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class Properties {

    @JsonProperty("totalDistance")
    private Integer totalDistance;

    @JsonProperty("totalTime")
    private Integer totalTime;

    @JsonProperty("departureTime")
    private String departureTime;

    @JsonProperty("arrivalTime")
    private String arrivalTime;

    @JsonProperty("pointType")
    private String pointType;

    @JsonProperty("time")
    private Integer time;
}
