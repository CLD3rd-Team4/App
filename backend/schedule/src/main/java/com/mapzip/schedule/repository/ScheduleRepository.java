package com.mapzip.schedule.repository;

import com.mapzip.schedule.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Schedule 엔티티에 대한 데이터베이스 작업을 처리하는 리포지토리입니다.
 */
public interface ScheduleRepository extends JpaRepository<Schedule, String> {

    /**
     * 특정 사용자의 모든 스케줄을 생성일자 내림차순으로 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 스케줄 엔티티 리스트
     */
    List<Schedule> findByUserIdOrderByCreatedAtDesc(String userId);
}
