package com.mapzip.recommend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReviewStatsDto {
    private double averageRating;
    private String representativeReview;
}
