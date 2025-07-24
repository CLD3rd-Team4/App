package com.mapzip.schedule.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 사용자가 선택한 맛집 정보를 담는 엔티티 클래스입니다.
 * selected_restaurants 테이블과 매핑됩니다.
 */
@Entity
@Getter
@Setter
@Table(name = "selected_restaurants")
public class SelectedRestaurant {

    /**
     * 시간 슬롯의 ID (Primary Key, Foreign Key)
     * MealTimeSlot 엔티티와 일대일 관계를 맺습니다.
     */
    @Id
    @Column(name = "slot_id", length = 50)
    private String slotId;

    /**
     * MealTimeSlot 엔티티와 매핑합니다.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "slot_id")
    private MealTimeSlot mealTimeSlot;

    /**
     * 선택된 맛집의 ID
     */
    @Column(name = "restaurant_id", nullable = false, length = 100)
    private String restaurantId;

    /**
     * 선택된 맛집의 이름
     */
    @Column(nullable = false, length = 200)
    private String name;

    /**
     * 예정된 식사/간식 시간
     */
    @Column(name = "scheduled_time", length = 20)
    private String scheduledTime;

    /**
     * 맛집 상세 정보 URL
     */
    @Lob
    @Column(name = "detail_url")
    private String detailUrl;

    /**
     * 맛집 선택 시간
     */
    @CreationTimestamp
    @Column(name = "selected_at", updatable = false)
    private LocalDateTime selectedAt;
}
