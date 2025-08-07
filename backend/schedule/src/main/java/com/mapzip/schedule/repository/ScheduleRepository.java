package com.mapzip.schedule.repository;

import com.mapzip.schedule.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    

    /**
     * 특정 사용자의 모든 스케줄의 is_selected 필드를 일괄적으로 업데이트합니다.
     *
     * @param userId     사용자 ID
     * @param isSelected 변경할 선택 상태
     */
    @Modifying
    @Query("UPDATE Schedule s SET s.isSelected = :isSelected WHERE s.userId = :userId")
    void updateIsSelectedByUserId(@Param("userId") String userId, @Param("isSelected") boolean isSelected);
}
