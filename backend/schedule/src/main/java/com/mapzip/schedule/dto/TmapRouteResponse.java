package com.mapzip.schedule.dto;

import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TmapRouteResponse {
    private String type;
    private List<Feature> features;
}
