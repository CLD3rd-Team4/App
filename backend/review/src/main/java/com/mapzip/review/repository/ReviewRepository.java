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
}