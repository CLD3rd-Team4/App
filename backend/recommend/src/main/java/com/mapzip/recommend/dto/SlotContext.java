package com.mapzip.recommend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SlotContext {
    private String scheduledTime; // "오전/오후 HH:mm" 혹은 "HH:mm"
    private int mealType;         // 0=MEAL, 1=SNACK
}
