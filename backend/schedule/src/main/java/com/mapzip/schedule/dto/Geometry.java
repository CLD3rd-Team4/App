package com.mapzip.schedule.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class Geometry {

    @JsonProperty("type")
    private String type;

    @JsonProperty("coordinates")
    private Object coordinates;
}
