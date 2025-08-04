package com.mapzip.review.repository;

import com.mapzip.review.entity.ReviewEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;

import java.time.Instant;
import java.util.List;
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
     * 추천 서버용: 지역별 고품질 리뷰 조회
     * - OCR 검증된 리뷰 우선
     * - 평점 3점 이상 리뷰만 조회
     * - 최신순 정렬
     */
    public List<ReviewEntity> findHighQualityReviewsForRecommendation(int page, int size) {
        // DynamoDB Scan을 사용하여 전체 테이블 스캔 (비효율적이지만 필터링 조건이 복잡함)
        ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .filterExpression(Expression.builder()
                        .expression("rating >= :minRating AND is_verified = :verified")
                        .putExpressionValue(":minRating", AttributeValue.builder().n("3").build())
                        .putExpressionValue(":verified", AttributeValue.builder().bool(true).build())
                        .build())
                .build();

        return reviewTable.scan(scanRequest)
                .stream()
                .flatMap(scanPage -> scanPage.items().stream())
                .sorted((r1, r2) -> r2.getCreatedAt().compareTo(r1.getCreatedAt())) // 최신순
                .skip((long) page * size)
                .limit(size)
                .collect(Collectors.toList());
    }

    /**
     * 추천 서버용: 특정 식당들의 최신 리뷰 조회
     * @param restaurantIds 식당 ID 목록
     * @param maxReviewsPerRestaurant 식당별 최대 리뷰 개수
     */
    public List<ReviewEntity> findRecentReviewsByRestaurantIds(List<String> restaurantIds, int maxReviewsPerRestaurant) {
        return restaurantIds.stream()
                .flatMap(restaurantId -> 
                    findByRestaurantId(restaurantId, 0, maxReviewsPerRestaurant).stream())
                .collect(Collectors.toList());
    }

    /**
     * 추천 서버용: 식당 주소 기반 리뷰 검색 (부분 매칭)
     * 실제 운영에서는 ElasticSearch나 별도 검색 엔진 사용 권장
     */
    public List<ReviewEntity> findReviewsByAddressPattern(String addressPattern, int page, int size) {
        ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .filterExpression(Expression.builder()
                        .expression("contains(restaurant_address, :addressPattern)")
                        .putExpressionValue(":addressPattern", AttributeValue.builder().s(addressPattern).build())
                        .build())
                .build();

        return reviewTable.scan(scanRequest)
                .stream()
                .flatMap(scanPage -> scanPage.items().stream())
                .sorted((r1, r2) -> r2.getCreatedAt().compareTo(r1.getCreatedAt()))
                .skip((long) page * size)
                .limit(size)
                .collect(Collectors.toList());
    }
}