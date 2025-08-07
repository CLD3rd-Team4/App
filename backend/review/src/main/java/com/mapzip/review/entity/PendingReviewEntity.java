package com.mapzip.review.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;

@DynamoDbBean
public class PendingReviewEntity {

    private String userId;  // PK: 사용자 ID
    private String scheduledTimeRestaurantId;  // SK: "12:00#restaurant123" 
    private String restaurantId;
    private String placeName;
    private String addressName;
    private String placeUrl;
    private String scheduledTime;
    private Boolean isCompleted; // false: 미작성, true: 작성완료
    private Instant createdAt;
    private Instant updatedAt;

    public PendingReviewEntity() {}

    @DynamoDbPartitionKey
    @DynamoDbAttribute("user_id")
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("scheduled_time_restaurant_id")
    public String getScheduledTimeRestaurantId() {
        return scheduledTimeRestaurantId;
    }

    public void setScheduledTimeRestaurantId(String scheduledTimeRestaurantId) {
        this.scheduledTimeRestaurantId = scheduledTimeRestaurantId;
    }

    @DynamoDbAttribute("restaurant_id")
    public String getRestaurantId() {
        return restaurantId;
    }

    public void setRestaurantId(String restaurantId) {
        this.restaurantId = restaurantId;
    }

    @DynamoDbAttribute("place_name")
    public String getPlaceName() {
        return placeName;
    }

    public void setPlaceName(String placeName) {
        this.placeName = placeName;
    }

    @DynamoDbAttribute("address_name")
    public String getAddressName() {
        return addressName;
    }

    public void setAddressName(String addressName) {
        this.addressName = addressName;
    }

    @DynamoDbAttribute("place_url")
    public String getPlaceUrl() {
        return placeUrl;
    }

    public void setPlaceUrl(String placeUrl) {
        this.placeUrl = placeUrl;
    }

    @DynamoDbAttribute("scheduled_time")
    public String getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(String scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    @DynamoDbAttribute("is_completed")
    public Boolean getIsCompleted() {
        return isCompleted;
    }

    public void setIsCompleted(Boolean isCompleted) {
        this.isCompleted = isCompleted;
    }

    @DynamoDbAttribute("created_at")
    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @DynamoDbAttribute("updated_at")
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    // 복합키 생성 헬퍼 메서드
    public void generateCompositeKey() {
        if (this.scheduledTime != null && this.restaurantId != null) {
            this.scheduledTimeRestaurantId = this.scheduledTime + "#" + this.restaurantId;
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
        if (this.isCompleted == null) {
            this.isCompleted = false; // 기본값: 미작성 상태
        }
    }
    
    // 복합키에서 스케줄 시간 추출
    public static String extractScheduledTimeFromCompositeKey(String compositeKey) {
        if (compositeKey != null && compositeKey.contains("#")) {
            return compositeKey.split("#")[0];
        }
        return null;
    }
    
    // 복합키에서 식당 ID 추출
    public static String extractRestaurantIdFromCompositeKey(String compositeKey) {
        if (compositeKey != null && compositeKey.contains("#")) {
            return compositeKey.split("#")[1];
        }
        return null;
    }
}