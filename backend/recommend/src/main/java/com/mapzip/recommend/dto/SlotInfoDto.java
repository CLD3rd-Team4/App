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
    private double lat;
    private double lon;
    private String scheduledTime; 
    private String mealType;
    private int radius;
}
