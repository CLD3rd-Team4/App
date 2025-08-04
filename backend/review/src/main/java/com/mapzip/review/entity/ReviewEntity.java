package com.mapzip.review.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@DynamoDbBean
public class ReviewEntity {

    private String restaurantId;  
    private String createdAtUserId;  // 복합키: "2024-01-01T12:00:00Z#user123"      
    private String userId;
    private String restaurantName;
    private String restaurantAddress;
    private Integer rating;
    private String content;
    private List<String> imageUrls;
    private String visitDate;
    private Boolean isVerified;
    private Instant createdAt;
    private Instant updatedAt;

    public ReviewEntity() {}

    @DynamoDbPartitionKey
    @DynamoDbAttribute("restaurant_id")
    public String getRestaurantId() {
        return restaurantId;
    }

    public void setRestaurantId(String restaurantId) {
        this.restaurantId = restaurantId;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("created_at_user_id")
    public String getCreatedAtUserId() {
        return createdAtUserId;
    }

    public void setCreatedAtUserId(String createdAtUserId) {
        this.createdAtUserId = createdAtUserId;
    }
    
    // 리뷰 ID는 복합키(Sort Key)와 동일
    public String getReviewId() {
        return this.createdAtUserId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "UserIdIndex")
    @DynamoDbAttribute("user_id")
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @DynamoDbAttribute("restaurant_name")
    public String getRestaurantName() {
        return restaurantName;
    }

    public void setRestaurantName(String restaurantName) {
        this.restaurantName = restaurantName;
    }

    @DynamoDbAttribute("restaurant_address")
    public String getRestaurantAddress() {
        return restaurantAddress;
    }

    public void setRestaurantAddress(String restaurantAddress) {
        this.restaurantAddress = restaurantAddress;
    }

    @DynamoDbAttribute("rating")  
    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    @DynamoDbAttribute("content")
    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @DynamoDbAttribute("image_urls")
    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    @DynamoDbAttribute("visit_date")
    public String getVisitDate() {
        return visitDate;
    }

    public void setVisitDate(String visitDate) {
        this.visitDate = visitDate;
    }

    @DynamoDbAttribute("is_verified")
    public Boolean getIsVerified() {
        return isVerified;
    }

    public void setIsVerified(Boolean isVerified) {
        this.isVerified = isVerified;
    }

    @DynamoDbAttribute("created_at")
    @DynamoDbSecondarySortKey(indexNames = "UserIdIndex")
    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    // GSI를 위한 ISO 문자열 형태의 created_at
    public String getCreatedAtString() {
        return createdAt != null ? createdAt.toString() : null;
    }

    @DynamoDbAttribute("updated_at")
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    // 엔티티 생성 시 복합키 자동 생성
    public void generateCompositeKey() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.userId != null) {
            this.createdAtUserId = this.createdAt.toString() + "#" + this.userId;
        }
    }
    
    // 사용자 ID와 생성 시간에서 복합키 생성
    public static String createCompositeKey(String userId, Instant createdAt) {
        return createdAt.toString() + "#" + userId;
    }
    
    // 복합키에서 사용자 ID 추출
    public static String extractUserIdFromCompositeKey(String compositeKey) {
        if (compositeKey != null && compositeKey.contains("#")) {
            return compositeKey.split("#")[1];
        }
        return null;
    }
    
    // 복합키에서 생성 시간 추출
    public static Instant extractCreatedAtFromCompositeKey(String compositeKey) {
        if (compositeKey != null && compositeKey.contains("#")) {
            return Instant.parse(compositeKey.split("#")[0]);
        }
        return null;
    }
}