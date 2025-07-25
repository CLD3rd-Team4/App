package com.mapzip.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TmapLocation {
    private String name;
    private double lon;
    private double lat;
}
