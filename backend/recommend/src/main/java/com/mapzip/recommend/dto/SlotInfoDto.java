package com.mapzip.recommend.dto;

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
public class SlotInfoDto {
    private String slotId;
    private String lat;
    private String lon;
    private String scheduledTime; 
    private int mealType;
    private int radius;
}
