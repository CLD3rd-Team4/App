package com.mapzip.schedule.repository;

import com.mapzip.schedule.entity.MealTimeSlot;
import com.mapzip.schedule.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MealTimeSlot 엔티티에 대한 데이터베이스 작업을 처리하는 리포지토리입니다.
 */
@Repository
public interface MealTimeSlotRepository extends JpaRepository<MealTimeSlot, String> {

    int countBySchedule(Schedule schedule);

    List<MealTimeSlot> findBySchedule(Schedule schedule);

}
