package com.mapzip.review.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@DynamoDbBean
public class ReviewEntity implements java.io.Serializable {

    private static final long serialVersionUID = 1L;


    private String restaurantId;  
    private String createdAtUserId;  // 복합키(Sort Key): "2024-01-01T12:00:00Z#{userId}" - 실질적인 reviewId 역할
    private String userId;
    private String restaurantName;
    private String restaurantAddress;
    private Integer rating;
    private String content;
    private List<String> imageUrls;
    private String visitDate;
    private Boolean isVerified;
    private String reviewStatus;
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
    
    /**
     * 프론트엔드 호환성을 위한 reviewId 접근자
     * 형식: "2024-01-01T12:00:00Z_userId"
     */
    public String getReviewId() {
        System.out.println("=== getReviewId() called ===");
        System.out.println("createdAtUserId: " + this.createdAtUserId);
        System.out.println("createdAt: " + this.createdAt);
        System.out.println("userId: " + this.userId);
        if (this.createdAtUserId == null && this.createdAt != null && this.userId != null) {
            System.out.println("createdAtUserId is null, generating: " + this.createdAt.toString() + "_" + this.userId);
            return this.createdAt.toString() + "_" + this.userId;
        }
        System.out.println("returning: " + this.createdAtUserId);
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
    @DynamoDbSecondarySortKey(indexNames = "RatingIndex")
    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    // 평점 기반 GSI를 위한 카테고리 필드 추가
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
    public Boolean getIsVerified() {
        return isVerified;
    }

    public void setIsVerified(Boolean isVerified) {
        this.isVerified = isVerified;
    }

    @DynamoDbAttribute("review_status")
    public String getReviewStatus() {
        // null 안전성을 위한 기본값 반환 (기존 데이터와의 호환성)
        if (reviewStatus == null || reviewStatus.isEmpty()) {
            return "PUBLISHED";
        }
        return reviewStatus;
    }
    
    // StatusIndex GSI는 기존 데이터 호환성 문제로 비활성화
    // 향후 필요시 데이터 마이그레이션 후 활성화 가능

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    @DynamoDbAttribute("created_at_instant")
    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    // GSI를 위한 ISO 문자열 형태의 created_at (Terraform과 일치시킴)
    @DynamoDbSecondarySortKey(indexNames = {"UserIdIndex", "StatusIndex", "RecommendationIndex", "AddressIndex"})
    @DynamoDbAttribute("created_at")
    public String getCreatedAtForGsi() {
        return createdAt != null ? createdAt.toString() : null;
    }
    
    public void setCreatedAtForGsi(String createdAtForGsi) {
        // DynamoDB Enhanced Client를 위한 setter (실제로는 사용하지 않음)
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
            this.createdAtUserId = this.createdAt.toString() + "_" + this.userId;
        }
        
        // GSI 필드들도 명시적으로 설정하여 null 값 방지
        if (this.reviewStatus == null || this.reviewStatus.isEmpty()) {
            this.reviewStatus = "PUBLISHED";
        }
        
        // 디버깅을 위한 로깅 추가 (더 상세하게)
        System.out.println("=== ReviewEntity.generateCompositeKey() ===");
        System.out.println("UserId: " + this.userId);
        System.out.println("CreatedAt: " + this.createdAt);
        System.out.println("Generated CreatedAtUserId (SORT KEY): " + this.createdAtUserId);
        System.out.println("CreatedAtForGsi: " + this.getCreatedAtForGsi());
        System.out.println("ReviewStatus: " + this.reviewStatus);
        System.out.println("Rating: " + this.rating);
        System.out.println("IsVerified: " + this.isVerified);
        System.out.println("RestaurantId: " + this.restaurantId);
        System.out.println("=== END generateCompositeKey() ===");
    }
    
    // 사용자 ID와 생성 시간에서 복합키 생성
    public static String createCompositeKey(String userId, Instant createdAt) {
        return createdAt.toString() + "_" + userId;
    }
    
    // 복합키에서 사용자 ID 추출
    public static String extractUserIdFromCompositeKey(String compositeKey) {
        if (compositeKey != null && compositeKey.contains("_")) {
            String[] parts = compositeKey.split("_");
            return parts.length > 1 ? parts[parts.length - 1] : null;
        }
        return null;
    }
    
    // 복합키에서 생성 시간 추출
    public static Instant extractCreatedAtFromCompositeKey(String compositeKey) {
        if (compositeKey != null && compositeKey.contains("_")) {
            String[] parts = compositeKey.split("_");
            if (parts.length > 1) {
                // timestamp 부분은 마지막 _ 앞까지
                String timestampPart = compositeKey.substring(0, compositeKey.lastIndexOf("_"));
                return Instant.parse(timestampPart);
            }
        }
        return null;
    }
}
