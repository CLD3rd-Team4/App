package com.mapzip.schedule.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 식사/간식 시간 슬롯 정보를 담는 엔티티 클래스입니다.
 * meal_time_slots 테이블과 매핑됩니다.
 */
@Entity
@Getter
@Setter
@Table(name = "meal_time_slots")
public class MealTimeSlot {

    /**
     * 시간 슬롯의 고유 ID (Primary Key)
     */
    @Id
    @Column(length = 50)
    private String id;

    /**
     * 이 시간 슬롯이 속한 스케줄 (Foreign Key)
     * Schedule 엔티티와 다대일 관계를 맺습니다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    /**
     * 식사/간식 구분 (0: 식사, 1: 간식)
     */
    @Column(name = "meal_type", nullable = false)
    private Integer mealType;

    /**
     * 예정된 식사/간식 시간 (예: "오후 12:30")
     */
    @Column(name = "scheduled_time", nullable = false, length = 20)
    private String scheduledTime;

    /**
     * 맛집 검색 반경 (미터 단위)
     */
    @Column(nullable = false)
    private Integer radius = 1000; // 기본값 1000m

    /**
     * 경로상 계산된 예상 식사/간식 위치 (JSON 형태로 저장)
     * MealLocation 객체를 JSONB 타입으로 변환하여 저장합니다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "calculated_location", columnDefinition = "jsonb")
    private String calculatedLocation;

    /**
     * 레코드 생성 시간
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
