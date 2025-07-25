package com.mapzip.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class WaypointsContainer {
    private List<TmapWaypoint> wayPoint;
}
