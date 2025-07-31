package com.mapzip.recommend.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecommendRequestDto {
    private String userId;
    private String scheduleId;
    private List<String> recommendationRequestIds;

    private String kakaoPlaceListJson;

    private String userNote;
    private String purpose;
    private List<String> companions;
}
