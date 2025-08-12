package com.mapzip.review.controller;

import com.mapzip.review.dto.OcrResultDto;
import com.mapzip.review.entity.PendingReviewEntity;
import com.mapzip.review.entity.ReviewEntity;
import com.mapzip.review.service.ReviewService;
import org.springframework.data.redis.core.RedisTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Optional;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/review")
@CrossOrigin(origins = {"https://www.mapzip.shop", "https://mapzip.shop"})
public class ReviewController {
    
    private static final Logger logger = LoggerFactory.getLogger(ReviewController.class);
    
    private final ReviewService reviewService;
    private final RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    public ReviewController(ReviewService reviewService, RedisTemplate<String, Object> redisTemplate) {
        this.reviewService = reviewService;
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * 영수증 OCR 검증 API
     * 프론트엔드에서 이미지 업로드 후 OCR 검증 결과를 받는 용도
     */
    @PostMapping(value = "/verify-receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> verifyReceipt(
            @RequestHeader("x-user-id") String userId,
            @RequestParam("receiptImage") MultipartFile receiptImage,
            @RequestParam("expectedRestaurantName") String expectedRestaurantName,
            @RequestParam("expectedAddress") String expectedAddress) {
        
        try {
            logger.info("Verifying receipt for user: {}, restaurant: {}", userId, expectedRestaurantName);
            
            // 파일 검증
            if (receiptImage.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "영수증 이미지가 필요합니다."));
            }
            
            // OCR 처리
            OcrResultDto ocrResult = reviewService.verifyReceipt(
                receiptImage.getBytes(), expectedRestaurantName, expectedAddress);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("ocrResult", ocrResult);
            response.put("message", ocrResult.isValid() ? "영수증 검증 성공" : "영수증 검증 실패");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error verifying receipt", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", "영수증 검증 중 오류가 발생했습니다."));
        }
    }
    
    /**
     * 리뷰 작성 API (이미지 포함)
     * 프론트엔드에서 멀티파트 데이터로 리뷰와 이미지를 함께 전송
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> createReview(
            @RequestHeader("x-user-id") String userId,
            @RequestParam("restaurantId") String restaurantId,
            @RequestParam("restaurantName") String restaurantName,
            @RequestParam("restaurantAddress") String restaurantAddress,
            @RequestParam("rating") int rating,
            @RequestParam("content") String content,
            @RequestParam(value = "receiptImages", required = false) List<MultipartFile> receiptImages,
            @RequestParam(value = "reviewImages", required = false) List<MultipartFile> reviewImages,
            @RequestParam(value = "scheduledTime", required = false) String scheduledTime,
            @RequestParam(value = "visitDate", required = false) String visitDate) {
        
        try {
            logger.info("Creating review for user: {}, restaurant: {}", userId, restaurantId);
            
            // 영수증 이미지 변환
            List<byte[]> receiptImageBytes = new ArrayList<>();
            if (receiptImages != null) {
                for (MultipartFile file : receiptImages) {
                    if (!file.isEmpty()) {
                        receiptImageBytes.add(file.getBytes());
                    }
                }
            }
            
            // 리뷰 이미지 변환
            List<byte[]> reviewImageBytes = new ArrayList<>();
            if (reviewImages != null) {
                for (MultipartFile file : reviewImages) {
                    if (!file.isEmpty()) {
                        reviewImageBytes.add(file.getBytes());
                    }
                }
            }
            
            // 리뷰 생성 (방문 날짜 포함)
            ReviewService.ReviewCreateResult result = reviewService.createReview(
                userId, restaurantId, restaurantName, restaurantAddress, 
                rating, content, receiptImageBytes, reviewImageBytes, visitDate);
            
            // 리뷰 작성 성공 시 관련 미작성 리뷰를 완료 처리
            if (result.isSuccess() && scheduledTime != null) {
                try {
                    reviewService.markPendingReviewAsCompleted(userId, restaurantId, scheduledTime);
                    logger.info("Marked pending review as completed for user: {}, restaurant: {}", userId, restaurantId);
                } catch (Exception e) {
                    logger.warn("Failed to mark pending review as completed, but review was created successfully", e);
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("reviewId", result.getReview() != null ? result.getReview().getReviewId() : null);
            response.put("isVerified", result.getOcrResult() != null ? result.getOcrResult().isValid() : false);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error creating review", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", "리뷰 작성 중 오류가 발생했습니다."));
        }
    }
    
    // === 리뷰 조회 API ===
    
    /**
     * 사용자의 작성된 리뷰 목록 조회
     */
    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> getUserReviews(
            @RequestHeader("x-user-id") String userId,
            @RequestParam(defaultValue = "0") int page,  // 0부터 시작
            @RequestParam(defaultValue = "10") int size) {
        try {
            logger.info("Getting user reviews for user: {}, page: {}, size: {}", userId, page, size);
            
            // 실제 서비스 로직 호출
            List<ReviewEntity> reviews = reviewService.getUserReviews(userId, page, size);
            long totalCount = reviewService.getUserReviewsCount(userId);
            
            // 다음 페이지 존재 여부 계산
            boolean hasNext = (page + 1) * size < totalCount;
            
            // 리뷰 데이터를 Map으로 변환
            List<Map<String, Object>> reviewData = reviews.stream()
                .map(review -> {
                    Map<String, Object> reviewMap = new HashMap<>();
                    reviewMap.put("reviewId", review.getReviewId());
                    reviewMap.put("restaurantId", review.getRestaurantId());
                    reviewMap.put("restaurantName", review.getRestaurantName() != null ? review.getRestaurantName() : "");
                    reviewMap.put("restaurantAddress", review.getRestaurantAddress() != null ? review.getRestaurantAddress() : "");
                    reviewMap.put("rating", review.getRating());
                    reviewMap.put("content", review.getContent() != null ? review.getContent() : "");
                    reviewMap.put("imageUrls", review.getImageUrls() != null ? review.getImageUrls() : List.of());
                    reviewMap.put("visitDate", review.getVisitDate() != null ? review.getVisitDate() : "");
                    reviewMap.put("isVerified", review.getIsVerified() != null ? review.getIsVerified() : false);
                    reviewMap.put("createdAt", review.getCreatedAt().toString());
                    reviewMap.put("updatedAt", review.getUpdatedAt().toString());
                    return reviewMap;
                })
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", reviewData,
                "totalCount", totalCount,
                "currentPage", page,
                "totalPages", (totalCount + size - 1) / size,  // 전체 페이지 수
                "hasNext", hasNext
            ));
            
        } catch (Exception e) {
            logger.error("Error getting user reviews", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", "사용자 리뷰 목록 조회 실패"));
        }
    }
    
    // === 미작성 리뷰 관리 API ===
    
    /**
     * 사용자의 미작성 리뷰 목록 조회
     */
    @GetMapping("/pending")
    public ResponseEntity<Map<String, Object>> getPendingReviews(@RequestHeader("x-user-id") String userId) {
        try {
            logger.info("Getting pending reviews for user: {}", userId);
            
            List<PendingReviewEntity> pendingReviews = reviewService.getPendingReviewsByUserId(userId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", pendingReviews,
                "count", pendingReviews.size()
            ));
            
        } catch (Exception e) {
            logger.error("Error getting pending reviews", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", "미작성 리뷰 목록 조회 실패"));
        }
    }
    
    /**
     * 미작성 리뷰 삭제 (사용자가 안간 경우)
     */
    @DeleteMapping("/pending/{restaurantId}")
    public ResponseEntity<Map<String, Object>> deletePendingReview(
            @RequestHeader("x-user-id") String userId,
            @PathVariable String restaurantId,
            @RequestParam String scheduledTime) {
        try {
            logger.info("Deleting pending review for user: {}, restaurant: {}", userId, restaurantId);
            
            boolean success = reviewService.deletePendingReview(userId, scheduledTime, restaurantId);
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "미작성 리뷰가 삭제되었습니다."
                ));
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            logger.error("Error deleting pending review", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", "미작성 리뷰 삭제 실패"));
        }
    }
    
    /**
     * 작성된 리뷰 삭제
     */
    @DeleteMapping("/{restaurantId}/{reviewId}")
    public ResponseEntity<Map<String, Object>> deleteReview(
            @RequestHeader("x-user-id") String userId,
            @PathVariable String restaurantId,
            @PathVariable String reviewId) {
        try {
            logger.info("Deleting review for user: {}, restaurant: {}, review: {}", userId, restaurantId, reviewId);
            
            reviewService.deleteReview(restaurantId, reviewId, userId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "리뷰가 삭제되었습니다."
            ));
            
        } catch (RuntimeException e) {
            logger.warn("Review deletion failed: {}", e.getMessage());
            if (e.getMessage().contains("권한이 없습니다") || e.getMessage().contains("찾을 수 없습니다")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", e.getMessage()));
            }
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error deleting review", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", "리뷰 삭제 실패"));
        }
    }
    
    /**
     * 특정 리뷰 상세 조회
     */
    @GetMapping("/{restaurantId}/{reviewId}")
    public ResponseEntity<Map<String, Object>> getReview(
            @RequestHeader("x-user-id") String userId,
            @PathVariable String restaurantId,
            @PathVariable String reviewId) {
        try {
            logger.info("Getting review detail for user: {}, restaurant: {}, review: {}", userId, restaurantId, reviewId);
            
            Optional<ReviewEntity> reviewOpt = reviewService.getReviewById(restaurantId, reviewId);
            
            if (reviewOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            ReviewEntity review = reviewOpt.get();
            
            // 작성자가 아닌 경우에도 리뷰는 조회 가능 (공개 정보)
            Map<String, Object> reviewData = new HashMap<>();
            reviewData.put("reviewId", review.getReviewId());
            reviewData.put("restaurantId", review.getRestaurantId());
            reviewData.put("restaurantName", review.getRestaurantName());
            reviewData.put("restaurantAddress", review.getRestaurantAddress());
            reviewData.put("userId", review.getUserId());
            reviewData.put("rating", review.getRating());
            reviewData.put("content", review.getContent());
            reviewData.put("imageUrls", review.getImageUrls());
            reviewData.put("visitDate", review.getVisitDate());
            reviewData.put("isVerified", review.getIsVerified());
            reviewData.put("createdAt", review.getCreatedAt());
            reviewData.put("updatedAt", review.getUpdatedAt());
            reviewData.put("isOwner", review.getUserId().equals(userId));
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", reviewData
            ));
            
        } catch (Exception e) {
            logger.error("Error getting review detail", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", "리뷰 조회 실패"));
        }
    }
    
    /**
     * 리뷰 수정
     */
    @PutMapping("/{restaurantId}/{reviewId}")
    public ResponseEntity<Map<String, Object>> updateReview(
            @RequestHeader("x-user-id") String userId,
            @PathVariable String restaurantId,
            @PathVariable String reviewId,
            @RequestParam("rating") int rating,
            @RequestParam("content") String content,
            @RequestParam(value = "reviewImages", required = false) List<MultipartFile> reviewImages) {
        try {
            logger.info("Updating review for user: {}, restaurant: {}, review: {}", userId, restaurantId, reviewId);
            
            // 권한 검증 (작성자만 수정 가능)
            Optional<ReviewEntity> existingReview = reviewService.getReviewById(restaurantId, reviewId);
            if (existingReview.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            if (!existingReview.get().getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "수정 권한이 없습니다."));
            }
            
            ReviewEntity updatedReview = reviewService.updateReviewWithImages(restaurantId, reviewId, userId, rating, content, reviewImages);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "리뷰가 수정되었습니다.",
                "data", Map.of(
                    "reviewId", updatedReview.getReviewId(),
                    "restaurantId", updatedReview.getRestaurantId(),
                    "rating", updatedReview.getRating(),
                    "content", updatedReview.getContent(),
                    "imageUrls", updatedReview.getImageUrls(),
                    "updatedAt", updatedReview.getUpdatedAt()
                )
            ));
            
        } catch (RuntimeException e) {
            logger.warn("Review update failed: {}", e.getMessage());
            if (e.getMessage().contains("권한이 없습니다") || e.getMessage().contains("찾을 수 없습니다")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", e.getMessage()));
            }
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error updating review", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", "리뷰 수정 실패"));
        }
    }
    
    /**
     * 특정 미작성 리뷰 상세 조회
     */
    @GetMapping("/pending/{restaurantId}/detail")
    public ResponseEntity<Map<String, Object>> getPendingReviewDetail(
            @RequestHeader("x-user-id") String userId,
            @PathVariable String restaurantId,
            @RequestParam String scheduledTime) {
        try {
            logger.info("Getting pending review detail for user: {}, restaurant: {}", userId, restaurantId);
            
            var pendingReview = reviewService.getPendingReviewDetail(userId, scheduledTime, restaurantId);
            
            if (pendingReview.isPresent()) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", pendingReview.get()
                ));
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            logger.error("Error getting pending review detail", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", "미작성 리뷰 조회 실패"));
        }
    }
    
    /**
     * 헬스 체크 API (서비스 및 레디스 상태 확인)
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("service", "review-service");
        health.put("timestamp", System.currentTimeMillis());
        
        // Redis 연결 상태 확인
        try {
            redisTemplate.opsForValue().set("health-check", "OK", java.time.Duration.ofSeconds(10));
            String result = (String) redisTemplate.opsForValue().get("health-check");
            
            if ("OK".equals(result)) {
                health.put("status", "UP");
                health.put("redis", Map.of(
                    "status", "UP", 
                    "connection", "ElastiCache connected successfully"
                ));
            } else {
                health.put("status", "PARTIAL");
                health.put("redis", Map.of(
                    "status", "DOWN", 
                    "connection", "Redis read/write test failed"
                ));
            }
        } catch (Exception e) {
            logger.error("레디스 연결 확인 실패", e);
            health.put("status", "PARTIAL");
            health.put("redis", Map.of(
                "status", "DOWN", 
                "connection", "Redis connection failed: " + e.getMessage()
            ));
        }
        
        return ResponseEntity.ok(health);
    }
}