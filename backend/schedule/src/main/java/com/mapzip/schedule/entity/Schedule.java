package com.mapzip.schedule.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 스케줄 정보를 담는 엔티티 클래스입니다.
 * schedules 테이블과 매핑됩니다.
 */
@Entity
@Getter
@Setter
@Table(name = "schedules")
public class Schedule {

    /**
     * 스케줄의 고유 ID (Primary Key)
     */
    @Id
    @Column(length = 50)
    private String id;

    /**
     * 스케줄을 생성한 사용자의 ID
     */
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    /**
     * 스케줄 제목
     */
    @Column(nullable = false, length = 200)
    private String title;

    /**
     * 출발 시간 (예: "오전 09:30")
     */
    @Column(name = "departure_time", nullable = false, length = 20)
    private String departureTime;

    /**
     * Tmap API를 통해 계산된 예상 도착 시간
     */
    @Column(name = "calculated_arrival_time", length = 20)
    private String calculatedArrivalTime;

    /**
     * 출발지 정보 (JSON 형태로 저장)
     * Location 객체를 JSONB 타입으로 변환하여 저장합니다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "departure_location", columnDefinition = "jsonb")
    private String departureLocation;

    /**
     * 목적지 정보 (JSON 형태로 저장)
     * Location 객체를 JSONB 타입으로 변환하여 저장합니다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "destination_location", columnDefinition = "jsonb")
    private String destinationLocation;

    /**
     * 경유지 목록 (JSON 형태로 저장)
     * Waypoint 객체 배열을 JSONB 타입으로 변환하여 저장합니다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String waypoints;

    /**
     * 사용자 메모
     */
    @Lob
    @Column(name = "user_note")
    private String userNote;

    /**
     * 여행 목적
     */
    @Column(length = 200)
    private String purpose;

    /**
     * 동행자 목록 (JSON 형태로 저장)
     * String 배열을 JSONB 타입으로 변환하여 저장합니다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String companions;

    /**
     * 레코드 생성 시간
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * 레코드 마지막 수정 시간
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private java.util.List<MealTimeSlot> mealTimeSlots = new java.util.ArrayList<>();

    public java.util.List<MealTimeSlot> getMealTimeSlots() {
        return mealTimeSlots;
    }

    public void setMealTimeSlots(java.util.List<MealTimeSlot> mealTimeSlots) {
        this.mealTimeSlots = mealTimeSlots;
    }

    /**
     * 연관된 모든 MealTimeSlot을 제거하고 관계를 정리하는 편의 메서드.
     */
    public void clearMealTimeSlots() {
        if (this.mealTimeSlots != null) {
            for (MealTimeSlot mealTimeSlot : this.mealTimeSlots) {
                mealTimeSlot.setSchedule(null); // MealTimeSlot에서의 연관 관계 제거
            }
            this.mealTimeSlots.clear();
        }
    }
}
