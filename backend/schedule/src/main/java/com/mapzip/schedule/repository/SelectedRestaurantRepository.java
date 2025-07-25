package com.mapzip.schedule.repository;

import com.mapzip.schedule.entity.Schedule;
import com.mapzip.schedule.entity.SelectedRestaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * SelectedRestaurant 엔티티에 대한 데이터베이스 작업을 처리하는 리포지토리입니다.
 */
@Repository
public interface SelectedRestaurantRepository extends JpaRepository<SelectedRestaurant, String> {

    int countBySchedule(Schedule schedule);

    List<SelectedRestaurant> findBySchedule(Schedule schedule);
}
