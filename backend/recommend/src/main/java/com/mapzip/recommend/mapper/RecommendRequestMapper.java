package com.mapzip.recommend.mapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapzip.recommend.dto.MultiSlotRecommendRequestDto;
import com.mapzip.recommend.dto.RecommendRequestDto;
import com.mapzip.recommend.dto.SlotInfoDto;
import com.mapzip.recommend.dto.kakao.KakaoSearchResponse;

public class RecommendRequestMapper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static RecommendRequestDto toRecommendRequestDto(
            MultiSlotRecommendRequestDto multiDto,
            Map<String, KakaoSearchResponse> kakaoResults
    ) {
        RecommendRequestDto dto = new RecommendRequestDto();
        List<String> scheduledTimes = multiDto.getSlots().stream()
        	    .map(SlotInfoDto::getScheduledTime)
        	    .collect(Collectors.toList());


        dto.setUserId(multiDto.getUserId());
        dto.setScheduleId(multiDto.getScheduleId());
        dto.setRecommendationRequestIds(multiDto.getRecommendationRequestIds());
        dto.setScheduledTimes(scheduledTimes);
        dto.setUserNote(multiDto.getUserNote());
        dto.setPurpose(multiDto.getPurpose());
        dto.setCompanions(multiDto.getCompanions());
        dto.setKakaoPlaceList(kakaoResults);

        return dto;
    }
}
