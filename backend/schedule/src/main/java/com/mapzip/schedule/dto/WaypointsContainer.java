package com.mapzip.schedule.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class WaypointsContainer {
    @JsonProperty("wayPoint")  // JSON에서 wayPoint로 직렬화
    private List<TmapWaypoint> wayPoint;
}
