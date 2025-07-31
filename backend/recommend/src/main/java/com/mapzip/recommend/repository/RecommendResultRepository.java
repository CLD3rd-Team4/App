package com.mapzip.recommend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.mapzip.recommend.entity.RecommendResultEntity;

@Repository
public interface RecommendResultRepository extends JpaRepository<RecommendResultEntity, Long> {
    List<RecommendResultEntity> findByUserId(String userId);
    List<RecommendResultEntity> findByScheduleId(String scheduleId);
}
