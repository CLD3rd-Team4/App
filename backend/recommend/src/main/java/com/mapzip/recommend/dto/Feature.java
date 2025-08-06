package com.mapzip.recommend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Feature {

    @JsonProperty("type")
    private String type;

    @JsonProperty("geometry")
    private Geometry geometry;

    @JsonProperty("properties")
    private Properties properties;
}
