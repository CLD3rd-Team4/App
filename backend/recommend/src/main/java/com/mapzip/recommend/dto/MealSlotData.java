package com.mapzip.recommend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MealSlotData {
    private String slotId;
    private int mealType;
    private String scheduledTime;
    private int radius;
}
