package com.mapzip.recommend.dto;

import java.util.List;
import java.util.Map;

import com.mapzip.recommend.dto.kakao.KakaoSearchResponse;

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
    List<String> scheduledTimes;

    private Map<String, KakaoSearchResponse> kakaoPlaceList;

    private String userNote;
    private String purpose;
    private List<String> companions;
}
