package com.mapzip.review.repository;

import com.mapzip.review.entity.PendingReviewEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    
    public PendingReviewRepository(DynamoDbEnhancedClient enhancedClient,
                                  @Value("${aws.dynamodb.pending-table-name:${aws.dynamodb.table-name:mapzip-dev-review}-pending}") String tableName) {
        this.enhancedClient = enhancedClient;
        this.pendingReviewTable = enhancedClient.table(tableName, 
                TableSchema.fromBean(PendingReviewEntity.class));
        logger.info("PendingReviewRepository initialized with table: {}", tableName);
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
        logger.info("=== STARTING PENDING REVIEW DELETION ===");
        logger.info("Deleting pending review for user: {}, compositeKey: {}", userId, compositeKey);
        
        try {
            // 삭제 전에 해당 데이터가 존재하는지 확인
            Optional<PendingReviewEntity> existing = findByUserIdAndCompositeKey(userId, compositeKey);
            if (existing.isEmpty()) {
                logger.warn("Pending review not found for deletion - user: {}, compositeKey: {}", userId, compositeKey);
                
                // 디버깅을 위해 사용자의 모든 미작성 리뷰 조회
                List<PendingReviewEntity> allPendingReviews = findByUserId(userId);
                logger.info("User {} has {} pending reviews:", userId, allPendingReviews.size());
                for (PendingReviewEntity review : allPendingReviews) {
                    logger.info("  - compositeKey: {}, restaurantId: {}, scheduledTime: {}", 
                               review.getRestaurantIdScheduledTime(), 
                               review.getRestaurantId(), 
                               review.getScheduledTime());
                }
                
                return false;
            }
            
            PendingReviewEntity reviewToDelete = existing.get();
            logger.info("Found pending review to delete: restaurantId={}, scheduledTime={}, compositeKey={}", 
                       reviewToDelete.getRestaurantId(), reviewToDelete.getScheduledTime(), 
                       reviewToDelete.getRestaurantIdScheduledTime());
            
            // DynamoDB Enhanced Client를 사용한 강제 삭제
            Key key = Key.builder()
                    .partitionValue(userId)
                    .sortValue(compositeKey)
                    .build();
            
            logger.info("Using deletion key: partitionValue={}, sortValue={}", userId, compositeKey);
            
            // 삭제 요청 생성 및 실행
            DeleteItemEnhancedRequest deleteRequest = DeleteItemEnhancedRequest.builder()
                    .key(key)
                    .build();
            
            PendingReviewEntity deletedItem = pendingReviewTable.deleteItem(deleteRequest);
            logger.info("DynamoDB deleteItem operation completed, returned item: {}", 
                       deletedItem != null ? "present" : "null");
            
            // 강제로 짧은 대기 후 재확인 (eventual consistency 고려)
            try {
                Thread.sleep(100); // 100ms 대기
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            
            // 삭제 후 재확인 - 더 강력한 검증
            Optional<PendingReviewEntity> afterDeletion = findByUserIdAndCompositeKey(userId, compositeKey);
            
            if (afterDeletion.isEmpty()) {
                logger.info("=== DELETION SUCCESSFUL ===");
                logger.info("Successfully deleted pending review for user: {}, compositeKey: {}", userId, compositeKey);
                
                // 추가 검증: 전체 목록에서도 제거되었는지 확인
                List<PendingReviewEntity> allReviewsAfterDeletion = findByUserId(userId);
                boolean stillExists = allReviewsAfterDeletion.stream()
                    .anyMatch(review -> compositeKey.equals(review.getRestaurantIdScheduledTime()));
                
                if (stillExists) {
                    logger.error("=== DELETION VERIFICATION FAILED ===");
                    logger.error("Item still exists in user's pending review list after deletion");
                    return false;
                } else {
                    logger.info("=== DELETION VERIFIED ===");
                    logger.info("Item successfully removed from user's pending review list");
                    return true;
                }
            } else {
                logger.error("=== DELETION FAILED ===");
                logger.error("Item still exists after deletion attempt. user: {}, compositeKey: {}", userId, compositeKey);
                return false;
            }
            
        } catch (Exception e) {
            logger.error("=== DELETION EXCEPTION ===");
            logger.error("Error deleting pending review for user: {}, compositeKey: {}", userId, compositeKey, e);
            e.printStackTrace();
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
                entity.setUpdatedAt(Instant.now().toString());
                
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
            
            // 디버깅: 생성된 엔티티 정보 로깅
            for (PendingReviewEntity entity : entities) {
                logger.info("Entity details - userId: {}, restaurantId: {}, scheduledTime: {}, compositeKey: {}, createdAt: {}", 
                           entity.getUserId(), entity.getRestaurantId(), entity.getScheduledTime(), 
                           entity.getRestaurantIdScheduledTime(), entity.getCreatedAt());
            }
            
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
            logger.error("Error saving batch of pending reviews: {}", e.getMessage(), e);
            e.printStackTrace();
            return false;
        }
    }
}