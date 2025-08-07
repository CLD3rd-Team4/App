package com.mapzip.review.repository;

import com.mapzip.review.entity.PendingReviewEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class PendingReviewRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(PendingReviewRepository.class);
    private final DynamoDbTable<PendingReviewEntity> pendingReviewTable;
    private final DynamoDbEnhancedClient enhancedClient;
    
    public PendingReviewRepository(DynamoDbEnhancedClient enhancedClient) {
        this.enhancedClient = enhancedClient;
        this.pendingReviewTable = enhancedClient.table("PendingReviews", 
                TableSchema.fromBean(PendingReviewEntity.class));
    }
    
    /**
     * 미작성 리뷰 저장
     */
    public PendingReviewEntity save(PendingReviewEntity entity) {
        logger.info("Saving pending review for user: {}, restaurant: {}", 
                   entity.getUserId(), entity.getRestaurantId());
        
        entity.generateCompositeKey();
        pendingReviewTable.putItem(entity);
        return entity;
    }
    
    /**
     * 사용자의 모든 미작성 리뷰 조회
     */
    public List<PendingReviewEntity> findByUserId(String userId) {
        logger.info("Finding pending reviews for user: {}", userId);
        
        try {
            QueryConditional queryConditional = QueryConditional
                    .keyEqualTo(Key.builder().partitionValue(userId).build());
            
            QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                    .queryConditional(queryConditional)
                    .scanIndexForward(false) // 최신순으로 정렬
                    .build();
            
            return pendingReviewTable.query(queryRequest)
                    .items()
                    .stream()
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            logger.error("Error finding pending reviews for user: {}", userId, e);
            return List.of();
        }
    }
    
    /**
     * 사용자의 미완료 리뷰만 조회
     */
    public List<PendingReviewEntity> findIncompleteByUserId(String userId) {
        logger.info("Finding incomplete pending reviews for user: {}", userId);
        
        return findByUserId(userId).stream()
                .filter(review -> !Boolean.TRUE.equals(review.getIsCompleted()))
                .collect(Collectors.toList());
    }
    
    /**
     * 특정 미작성 리뷰 조회
     */
    public Optional<PendingReviewEntity> findByUserIdAndCompositeKey(String userId, String compositeKey) {
        logger.info("Finding pending review for user: {}, compositeKey: {}", userId, compositeKey);
        
        try {
            Key key = Key.builder()
                    .partitionValue(userId)
                    .sortValue(compositeKey)
                    .build();
                    
            PendingReviewEntity result = pendingReviewTable.getItem(key);
            return Optional.ofNullable(result);
            
        } catch (Exception e) {
            logger.error("Error finding pending review for user: {}, compositeKey: {}", userId, compositeKey, e);
            return Optional.empty();
        }
    }
    
    /**
     * 미작성 리뷰 삭제 (사용자가 안간 경우)
     */
    public boolean delete(String userId, String compositeKey) {
        logger.info("Deleting pending review for user: {}, compositeKey: {}", userId, compositeKey);
        
        try {
            Key key = Key.builder()
                    .partitionValue(userId)
                    .sortValue(compositeKey)
                    .build();
                    
            pendingReviewTable.deleteItem(key);
            return true;
            
        } catch (Exception e) {
            logger.error("Error deleting pending review for user: {}, compositeKey: {}", userId, compositeKey, e);
            return false;
        }
    }
    
    /**
     * 미작성 리뷰를 완료 상태로 업데이트 (리뷰 작성 후)
     */
    public boolean markAsCompleted(String userId, String compositeKey) {
        logger.info("Marking pending review as completed for user: {}, compositeKey: {}", userId, compositeKey);
        
        try {
            Optional<PendingReviewEntity> existingReview = findByUserIdAndCompositeKey(userId, compositeKey);
            if (existingReview.isPresent()) {
                PendingReviewEntity entity = existingReview.get();
                entity.setIsCompleted(true);
                entity.setUpdatedAt(Instant.now());
                
                pendingReviewTable.putItem(entity);
                return true;
            }
            return false;
            
        } catch (Exception e) {
            logger.error("Error marking pending review as completed for user: {}, compositeKey: {}", userId, compositeKey, e);
            return false;
        }
    }
    
    /**
     * 배치로 여러 미작성 리뷰 저장
     */
    public boolean saveBatch(List<PendingReviewEntity> entities) {
        logger.info("Saving batch of {} pending reviews", entities.size());
        
        try {
            // 각 엔티티의 복합키 생성
            entities.forEach(PendingReviewEntity::generateCompositeKey);
            
            // 배치 쓰기 요청 생성
            WriteBatch.Builder<PendingReviewEntity> writeBatchBuilder = WriteBatch.builder(PendingReviewEntity.class)
                    .mappedTableResource(pendingReviewTable);
            
            for (PendingReviewEntity entity : entities) {
                writeBatchBuilder.addPutItem(entity);
            }
            
            // 배치 실행
            BatchWriteItemEnhancedRequest batchWriteRequest = BatchWriteItemEnhancedRequest.builder()
                    .addWriteBatch(writeBatchBuilder.build())
                    .build();
            
            enhancedClient.batchWriteItem(batchWriteRequest);
            return true;
            
        } catch (Exception e) {
            logger.error("Error saving batch of pending reviews", e);
            return false;
        }
    }
}