package com.mapzip.schedule.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class Feature {
    private String type;
    private Geometry geometry;
    private Properties properties;
}
