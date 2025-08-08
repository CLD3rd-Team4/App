package com.mapzip.review.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@DynamoDbBean
public class ReviewEntity {

    private String restaurantId;  
    private String createdAtUserId;  // 복합키: "2024-01-01T12:00:00Z#{userId}"      
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

    @DynamoDbSecondaryPartitionKey(indexNames = {"UserIdIndex", "VerifiedReviewsIndex", "RatingIndex"})
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
    @DynamoDbSecondarySortKey(indexNames = "RatingIndex")
    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    // 평점 기반 GSI를 위한 카테고리 필드 추가
    @DynamoDbSecondaryPartitionKey(indexNames = "RatingIndex")
    @DynamoDbAttribute("rating_category")
    public String getRatingCategory() {
        if (rating == null) return "RATING_0";
        return "RATING_" + rating;
    }
    
    public void setRatingCategory(String ratingCategory) {
        // DynamoDB Enhanced Client를 위한 setter
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
    @DynamoDbSecondarySortKey(indexNames = "VerifiedReviewsIndex")
    public Boolean getIsVerified() {
        return isVerified;
    }

    public void setIsVerified(Boolean isVerified) {
        this.isVerified = isVerified;
    }

    @DynamoDbAttribute("created_at")
    @DynamoDbSecondarySortKey(indexNames = {"UserIdIndex", "VerifiedReviewsIndex", "RatingIndex"})
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
    
    // 추천용 GSI를 위한 검증상태와 평점 결합 필드 (NPE 방지 강화)
    @DynamoDbSecondaryPartitionKey(indexNames = "RecommendationIndex")
    @DynamoDbAttribute("verified_rating_status")
    public String getVerifiedRatingStatus() {
        // null 체크 강화
        boolean verified = Boolean.TRUE.equals(isVerified);
        int safeRating = rating != null ? rating : 0;
        
        String verificationStatus = verified ? "VERIFIED" : "UNVERIFIED";
        String ratingStatus = safeRating >= 3 ? "HIGH_RATING" : "LOW_RATING";
        
        return verificationStatus + "#" + ratingStatus;
    }
    
    public void setVerifiedRatingStatus(String verifiedRatingStatus) {
        // DynamoDB Enhanced Client를 위한 setter (실제로는 사용하지 않음)
    }
    
    // 지역 기반 검색용 GSI를 위한 주소 해시 필드
    @DynamoDbSecondaryPartitionKey(indexNames = "AddressIndex")
    @DynamoDbAttribute("address_region")
    public String getAddressRegion() {
        if (restaurantAddress != null && !restaurantAddress.isEmpty()) {
            // 서울시 강남구, 경기도 성남시 등에서 주요 지역 추출
            String[] addressParts = restaurantAddress.split(" ");
            if (addressParts.length >= 2) {
                return addressParts[0] + " " + addressParts[1]; // "서울시 강남구"
            } else {
                return addressParts[0]; // "서울시"
            }
        }
        return "UNKNOWN";
    }
    
    public void setAddressRegion(String addressRegion) {
        // DynamoDB Enhanced Client를 위한 setter (실제로는 사용하지 않음)
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