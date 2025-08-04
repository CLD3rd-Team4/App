package com.mapzip.recommend.dto.kakao;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Meta {
    @JsonProperty("total_count")
    private int totalCount;
    @JsonProperty("pageable_count")
    private int pageableCount;
    @JsonProperty("is_end")
    private boolean isEnd;
    @JsonProperty("same_name")
    private SameName sameName;
}