package com.mapzip.schedule.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class Geometry {
    private String type;
    private List<Object> coordinates;
}
