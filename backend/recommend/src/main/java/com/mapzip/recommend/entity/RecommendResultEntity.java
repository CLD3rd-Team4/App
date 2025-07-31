package com.mapzip.recommend.entity;

import java.util.List;

import com.mapzip.recommend.dto.RecommendResultDto;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.*; 


@Entity
@Table(name = "recommend_result")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;

    private String scheduleId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "recommend_request_ids", joinColumns = @JoinColumn(name = "recommend_result_id"))
    @Column(name = "recommendation_request_id")
    private List<String> recommendationRequestIds;

    @Lob // → 긴 문자열 저장 (recommendPlaceListJson)
    private String recommendPlaceListJson;

    public static RecommendResultEntity fromDto(RecommendResultDto dto) {
        return RecommendResultEntity.builder()
            .userId(dto.getUserId())
            .scheduleId(dto.getScheduleId())
            .recommendationRequestIds(dto.getRecommendationRequestIds())
            .recommendPlaceListJson(dto.getRecommendPlaceListJson())
            .build();
    }
}
