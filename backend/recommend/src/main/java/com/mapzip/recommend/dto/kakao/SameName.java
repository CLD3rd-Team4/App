package com.mapzip.recommend.dto.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SameName {
    private List<String> region;
    private String keyword;
    @JsonProperty("selected_region")
    private String selectedRegion;
}