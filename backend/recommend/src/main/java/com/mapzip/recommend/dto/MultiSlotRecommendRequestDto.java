package com.mapzip.recommend.dto;


import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MultiSlotRecommendRequestDto {
    private String userId;
    private String scheduleId;
    private List<String> recommendationRequestIds;
    private List<SlotInfoDto> slots;
    private String userNote;
    private String purpose;
    private List<String> companions;
}
