package com.mapzip.review.controller;

import com.mapzip.review.dto.OcrResultDto;
import com.mapzip.review.entity.PendingReviewEntity;
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
     * 특정 미작성 리뷰 상세 조회
     */
    @GetMapping("/pending/{restaurantId}")
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