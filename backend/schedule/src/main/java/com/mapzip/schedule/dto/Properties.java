package com.mapzip.schedule.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class Properties {
    private Integer totalDistance;
    private Integer totalTime;
    private String departureTime;
    private String arrivalTime;
}
