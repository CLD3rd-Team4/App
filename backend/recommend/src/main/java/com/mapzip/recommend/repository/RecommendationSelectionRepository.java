package com.mapzip.recommend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.mapzip.recommend.entity.RecommendationSelectionEntity;

@Repository
public interface RecommendationSelectionRepository extends JpaRepository<RecommendationSelectionEntity, Long> {
	List<RecommendationSelectionEntity> findByUserIdAndScheduleId(String userId, String scheduleId);
	List<RecommendationSelectionEntity> findByUserId(String userId);
}