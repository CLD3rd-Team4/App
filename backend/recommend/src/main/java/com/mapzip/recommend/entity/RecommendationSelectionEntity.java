package com.mapzip.recommend.entity;


import java.time.LocalDate;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "recommendation_selection")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationSelectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;
    private String scheduleId;
    private String slotId;
    private String placeId;
    private String placeName;
    private String mealType;
    private String scheduledTime;
    private String reason;
    private String distance;
    private String placeUrl;
    private String addressName;
    private LocalDate selectedDate;
}

