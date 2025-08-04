package com.mapzip.recommend.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecommendResultDto {
    private String userId;
    private String scheduleId;
    private List<String> recommendationRequestIds;
    List<String> scheduledTimes;

    private String recommendPlaceListJson;

}
