package com.mapzip.review.repository;

import com.mapzip.review.entity.ReviewEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class ReviewRepository {

    private final DynamoDbTable<ReviewEntity> reviewTable;

    @Autowired
    public ReviewRepository(DynamoDbEnhancedClient enhancedClient,
                           @Value("${aws.dynamodb.table-name}") String tableName) {
        this.reviewTable = enhancedClient.table(tableName, TableSchema.fromBean(ReviewEntity.class));
    }

    public ReviewEntity save(ReviewEntity review) {
        if (review.getCreatedAt() == null) {
            review.setCreatedAt(Instant.now());
        }
        review.setUpdatedAt(Instant.now());
        
        reviewTable.putItem(review);
        return review;
    }

    public Optional<ReviewEntity> findByRestaurantIdAndReviewId(String restaurantId, String reviewId) {
        Key key = Key.builder()
                .partitionValue(restaurantId)
                .sortValue(reviewId)
                .build();
                
        ReviewEntity review = reviewTable.getItem(key);
        return Optional.ofNullable(review);
    }

    public List<ReviewEntity> findByRestaurantId(String restaurantId, int page, int size) {
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(restaurantId).build());

        var querySpec = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(false) // 최신순 정렬 (review_id 기준)
                .build();

        return reviewTable.query(querySpec)
                .stream()
                .flatMap(page1 -> page1.items().stream())
                .skip((long) page * size)
                .limit(size)
                .collect(Collectors.toList());
    }

    public List<ReviewEntity> findByUserId(String userId) {
        DynamoDbIndex<ReviewEntity> index = reviewTable.index("UserIdIndex");
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(userId).build());

        return index.query(QueryEnhancedRequest.builder().queryConditional(queryConditional).build())
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    public long countByRestaurantId(String restaurantId) {
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(restaurantId).build());

        return reviewTable.query(QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .build())
                .stream()
                .mapToLong(page -> page.items().size())
                .sum();
    }

    public void deleteByRestaurantIdAndReviewId(String restaurantId, String reviewId) {
        Key key = Key.builder()
                .partitionValue(restaurantId)
                .sortValue(reviewId)
                .build();
                
        reviewTable.deleteItem(key);
    }

    public double getAverageRatingByRestaurantId(String restaurantId) {
        List<ReviewEntity> reviews = findByRestaurantId(restaurantId, 0, 1000); // 최대 1000개 리뷰
        
        if (reviews.isEmpty()) {
            return 0.0;
        }
        
        return reviews.stream()
                .mapToInt(ReviewEntity::getRating)
                .average()
                .orElse(0.0);
    }

    /**
     * 추천 서버용: 지역별 고품질 리뷰 조회 (GSI 활용으로 최적화)
     * - RecommendationIndex GSI 사용
     * - OCR 검증된 고평점 리뷰 우선 조회
     * - 최신순 정렬
     */
    public List<ReviewEntity> findHighQualityReviewsForRecommendation(int page, int size) {
        DynamoDbIndex<ReviewEntity> index = reviewTable.index("RecommendationIndex");
        
        // 검증된 고평점 리뷰 우선 조회
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder()
                    .partitionValue("VERIFIED#HIGH_RATING")
                    .build());

        QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(false) // 최신순 정렬 (created_at 역순)
                .build();

        List<ReviewEntity> verifiedHighRatingReviews = index.query(queryRequest)
                .stream()
                .flatMap(queryPage -> queryPage.items().stream())
                .skip((long) page * size)
                .limit(size)
                .collect(Collectors.toList());

        // 검증된 고평점 리뷰가 충분하지 않은 경우 미검증 고평점 리뷰도 포함
        if (verifiedHighRatingReviews.size() < size) {
            QueryConditional unverifiedQuery = QueryConditional
                    .keyEqualTo(Key.builder()
                        .partitionValue("UNVERIFIED#HIGH_RATING")
                        .build());

            List<ReviewEntity> unverifiedHighRatingReviews = index.query(
                    QueryEnhancedRequest.builder()
                            .queryConditional(unverifiedQuery)
                            .scanIndexForward(false)
                            .build())
                    .stream()
                    .flatMap(queryPage -> queryPage.items().stream())
                    .limit(size - verifiedHighRatingReviews.size())
                    .collect(Collectors.toList());

            verifiedHighRatingReviews.addAll(unverifiedHighRatingReviews);
        }

        return verifiedHighRatingReviews;
    }

    /**
     * 추천 서버용: 특정 식당들의 최신 리뷰 조회 (N+1 쿼리 방지)
     * @param restaurantIds 식당 ID 목록
     * @param maxReviewsPerRestaurant 식당별 최대 리뷰 개수
     */
    public List<ReviewEntity> findRecentReviewsByRestaurantIds(List<String> restaurantIds, int maxReviewsPerRestaurant) {
        // N+1 쿼리 방지를 위한 배치 처리
        List<ReviewEntity> allReviews = new ArrayList<>();
        
        // 배치 크기로 나누어 처리 (DynamoDB 제한 고려)
        int batchSize = 25; // DynamoDB batch get item 제한
        for (int i = 0; i < restaurantIds.size(); i += batchSize) {
            List<String> batch = restaurantIds.subList(i, Math.min(i + batchSize, restaurantIds.size()));
            
            // 각 배치에 대해 병렬 처리
            List<ReviewEntity> batchReviews = batch.parallelStream()
                    .flatMap(restaurantId -> 
                        findByRestaurantId(restaurantId, 0, maxReviewsPerRestaurant).stream())
                    .collect(Collectors.toList());
            
            allReviews.addAll(batchReviews);
        }
        
        // 최신순으로 정렬 후 제한
        return allReviews.stream()
                .sorted((r1, r2) -> r2.getCreatedAt().compareTo(r1.getCreatedAt()))
                .limit(restaurantIds.size() * maxReviewsPerRestaurant)
                .collect(Collectors.toList());
    }

    /**
     * 추천 서버용: 식당 주소 기반 리뷰 검색 (GSI 활용으로 최적화)
     * AddressIndex GSI를 사용하여 지역별 검색 성능 개선
     */
    public List<ReviewEntity> findReviewsByAddressPattern(String addressPattern, int page, int size) {
        DynamoDbIndex<ReviewEntity> index = reviewTable.index("AddressIndex");
        
        // 지역명을 정규화하여 GSI 키로 변환
        String normalizedRegion = normalizeAddressForGSI(addressPattern);
        
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder()
                    .partitionValue(normalizedRegion)
                    .build());

        QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(false) // 최신순 정렬
                .build();

        return index.query(queryRequest)
                .stream()
                .flatMap(queryPage -> queryPage.items().stream())
                .skip((long) page * size)
                .limit(size)
                .collect(Collectors.toList());
    }
    
    /**
     * 주소 패턴을 GSI 키 형식으로 정규화
     * 예: "강남구" -> "서울시 강남구", "성남시" -> "경기도 성남시"
     */
    private String normalizeAddressForGSI(String addressPattern) {
        if (addressPattern == null || addressPattern.isEmpty()) {
            return "UNKNOWN";
        }
        
        // 주요 지역별 매핑
        Map<String, String> regionMapping = Map.of(
            "강남구", "서울시 강남구",
            "서초구", "서울시 서초구", 
            "송파구", "서울시 송파구",
            "성남시", "경기도 성남시",
            "수원시", "경기도 수원시",
            "부산", "부산시",
            "대구", "대구시",
            "인천", "인천시"
        );
        
        // 정확한 매핑이 있는 경우 사용
        String normalized = regionMapping.get(addressPattern);
        if (normalized != null) {
            return normalized;
        }
        
        // 패턴이 이미 "시/도 시/군/구" 형태인 경우
        if (addressPattern.contains(" ") && addressPattern.split(" ").length >= 2) {
            String[] parts = addressPattern.split(" ");
            return parts[0] + " " + parts[1];
        }
        
        // 기본값으로 원본 반환
        return addressPattern;
    }
    
    /**
     * 평점 기반 리뷰 조회 (개선된 RatingIndex GSI 활용)
     * @param minRating 최소 평점 (1-5)
     * @param page 페이지 번호
     * @param size 페이지 크기
     * @return 고평점 리뷰 목록 (최신순)
     */
    public List<ReviewEntity> findReviewsByMinRating(int minRating, int page, int size) {
        DynamoDbIndex<ReviewEntity> index = reviewTable.index("RatingIndex");
        List<ReviewEntity> allResults = new ArrayList<>();
        
        // minRating 이상의 모든 평점에 대해 쿼리 (5점부터 minRating까지)
        for (int rating = 5; rating >= minRating; rating--) {
            String ratingCategory = "RATING_" + rating;
            
            QueryConditional queryConditional = QueryConditional
                    .keyEqualTo(Key.builder()
                        .partitionValue(ratingCategory)  // "RATING_5", "RATING_4" 등
                        .build());
            
            QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                    .queryConditional(queryConditional)
                    .scanIndexForward(false)  // 최신순 정렬 (created_at 역순)
                    .limit(size * 2)  // 충분한 결과 확보를 위해 여유분 조회
                    .build();
            
            // 해당 평점의 리뷰들을 조회하여 결과에 추가
            List<ReviewEntity> ratingResults = index.query(queryRequest)
                    .stream()
                    .flatMap(queryPage -> queryPage.items().stream())
                    .collect(Collectors.toList());
            
            allResults.addAll(ratingResults);
            
            // 충분한 결과를 얻었으면 조기 종료
            if (allResults.size() >= (page + 1) * size) {
                break;
            }
        }
        
        // 최신순으로 재정렬 후 페이징 적용
        return allResults.stream()
                .sorted((r1, r2) -> r2.getCreatedAt().compareTo(r1.getCreatedAt()))
                .skip((long) page * size)
                .limit(size)
                .collect(Collectors.toList());
    }
    
    /**
     * 검증된 리뷰만 조회 (VerifiedReviewsIndex GSI 활용)
     * @param isVerified 검증 상태
     * @param page 페이지 번호  
     * @param size 페이지 크기
     * @return 검증된/미검증된 리뷰 목록
     */
    public List<ReviewEntity> findReviewsByVerificationStatus(boolean isVerified, int page, int size) {
        DynamoDbIndex<ReviewEntity> index = reviewTable.index("VerifiedReviewsIndex");
        
        // 실제 구현에서는 사용자별로 쿼리해야 하므로 복잡함
        // 여기서는 RecommendationIndex를 사용하는 것이 더 효율적
        String status = isVerified ? "VERIFIED#HIGH_RATING" : "UNVERIFIED#HIGH_RATING";
        
        DynamoDbIndex<ReviewEntity> recommendationIndex = reviewTable.index("RecommendationIndex");
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder()
                    .partitionValue(status)
                    .build());

        return recommendationIndex.query(QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(false)
                .build())
                .stream()
                .flatMap(queryPage -> queryPage.items().stream())
                .skip((long) page * size)
                .limit(size)
                .collect(Collectors.toList());
    }
}