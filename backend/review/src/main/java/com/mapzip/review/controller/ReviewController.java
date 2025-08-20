package com.mapzip.review.controller;

import com.mapzip.review.dto.OcrResultDto;
import com.mapzip.review.dto.VerifyReceiptRequest;
import com.mapzip.review.dto.CreateReviewRequest;
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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/review")
public class ReviewController {
    
    private static final Logger logger = LoggerFactory.getLogger(ReviewController.class);
    
    private final ReviewService reviewService;
    private final RedisTemplate<String, Object> valkeyTemplate;
    
    @Autowired
    public ReviewController(ReviewService reviewService, RedisTemplate<String, Object> redisTemplate) {
        this.reviewService = reviewService;
        this.valkeyTemplate = redisTemplate;
    }
    
    /**
     * 영수증 OCR 검증 API
     * 프론트엔드에서 이미지 업로드 후 OCR 검증 결과를 받는 용도
     */
    @PostMapping(value = "/verify-receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> verifyReceipt(
            @RequestHeader("x-user-id") String userId,
            @Valid @ModelAttribute VerifyReceiptRequest request) throws Exception {
        
        logger.info("Verifying receipt for user: {}, restaurant: {}", userId, request.getExpectedRestaurantName());
        
        try {
            // 입력값 검증
            if (request.getReceiptImage() == null || request.getReceiptImage().isEmpty()) {
                throw new IllegalArgumentException("영수증 이미지가 필요합니다.");
            }
            
            if (request.getExpectedRestaurantName() == null || request.getExpectedRestaurantName().trim().isEmpty()) {
                throw new IllegalArgumentException("식당명이 필요합니다.");
            }
            
            // 파일 크기 및 형식 검증
            if (request.getReceiptImage().getSize() > 10 * 1024 * 1024) { // 10MB 제한
                throw new IllegalArgumentException("이미지 크기는 10MB 이하여야 합니다.");
            }
            
            String contentType = request.getReceiptImage().getContentType();
            if (contentType == null || (!contentType.startsWith("image/"))) {
                throw new IllegalArgumentException("이미지 파일만 업로드 가능합니다.");
            }
            
            // OCR 처리
            OcrResultDto ocrResult = reviewService.verifyReceipt(
                request.getReceiptImage().getBytes(), 
                request.getExpectedRestaurantName(), 
                request.getExpectedAddress());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("ocrResult", ocrResult);
            response.put("message", ocrResult.isValid() ? "영수증 검증 성공" : "영수증 검증 실패");
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            logger.warn("OCR request validation failed for user: {}, error: {}", userId, e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            errorResponse.put("errorCode", "VALIDATION_ERROR");
            
            return ResponseEntity.badRequest().body(errorResponse);
            
        } catch (Exception e) {
            logger.error("OCR processing failed for user: {}", userId, e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "영수증 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
            errorResponse.put("errorCode", "OCR_PROCESSING_ERROR");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * 리뷰 작성 API (이미지 포함)
     * 프론트엔드에서 멀티파트 데이터로 리뷰와 이미지를 함께 전송
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> createReview(
            @RequestHeader("x-user-id") String userId,
            @Valid @ModelAttribute CreateReviewRequest request) throws Exception {
        
        logger.info("Creating review for user: {}, restaurant: {}", userId, request.getRestaurantId());
        
        // DTO로 입력값 검증이 자동 처리됨
        
        // 영수증 이미지 변환
        List<byte[]> receiptImageBytes = new ArrayList<>();
        if (request.getReceiptImages() != null) {
            for (MultipartFile file : request.getReceiptImages()) {
                if (!file.isEmpty()) {
                    receiptImageBytes.add(file.getBytes());
                }
            }
        }
        
        // 리뷰 이미지 변환
        List<byte[]> reviewImageBytes = new ArrayList<>();
        if (request.getReviewImages() != null) {
            for (MultipartFile file : request.getReviewImages()) {
                if (!file.isEmpty()) {
                    reviewImageBytes.add(file.getBytes());
                }
            }
        }
        
        // 리뷰 생성 (방문 날짜 포함)
        ReviewService.ReviewCreateResult result = reviewService.createReview(
            userId, request.getRestaurantId(), request.getRestaurantName(), request.getRestaurantAddress(), 
            request.getRating(), request.getContent(), receiptImageBytes, reviewImageBytes, request.getVisitDate());
        
        // 리뷰 작성 성공 시 관련 미작성 리뷰를 완료 처리
        if (result.isSuccess() && request.getScheduledTime() != null) {
            try {
                reviewService.markPendingReviewAsCompleted(userId, request.getRestaurantId(), request.getScheduledTime());
                logger.info("Marked pending review as completed for user: {}, restaurant: {}", userId, request.getRestaurantId());
            } catch (Exception e) {
                logger.warn("Failed to mark pending review as completed, but review was created successfully", e);
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", result.isSuccess());
        response.put("message", result.getMessage());
        
        // 성공한 경우 완전한 리뷰 데이터 반환
        if (result.isSuccess() && result.getReview() != null) {
            ReviewEntity review = result.getReview();
            Map<String, Object> reviewData = new HashMap<>();
            reviewData.put("reviewId", review.getReviewId());
            reviewData.put("restaurantId", review.getRestaurantId());
            reviewData.put("restaurantName", review.getRestaurantName());
            reviewData.put("restaurantAddress", review.getRestaurantAddress());
            reviewData.put("userId", review.getUserId());
            reviewData.put("rating", review.getRating());
            reviewData.put("content", review.getContent());
            reviewData.put("imageUrls", review.getImageUrls() != null ? review.getImageUrls() : List.of());
            reviewData.put("visitDate", review.getVisitDate());
            reviewData.put("isVerified", review.getIsVerified() != null ? review.getIsVerified() : false);
            reviewData.put("createdAt", review.getCreatedAt().toString());
            reviewData.put("updatedAt", review.getUpdatedAt().toString());
            
            response.put("data", reviewData);
        }
        
        // OCR 결과 정보도 포함
        if (result.getOcrResult() != null) {
            response.put("ocrResult", Map.of(
                "valid", result.getOcrResult().isValid(),
                "visitDate", result.getOcrResult().getVisitDate() != null ? result.getOcrResult().getVisitDate() : "",
                "totalAmount", result.getOcrResult().getTotalAmount() != null ? result.getOcrResult().getTotalAmount() : ""
            ));
        }
        
        return ResponseEntity.ok(response);
    }
    
    // === 리뷰 조회 API ===
    
    /**
     * 사용자의 작성된 리뷰 목록 조회
     */
    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> getUserReviews(
            @RequestHeader("x-user-id") String userId,
            @RequestParam(defaultValue = "0") int page,  // 0부터 시작
            @RequestParam(defaultValue = "10") int size) throws Exception {
        
        logger.info("Getting user reviews for user: {}, page: {}, size: {}", userId, page, size);
        
        // 페이지네이션 파라미터 검증
        if (page < 0) {
            throw new IllegalArgumentException("페이지는 0 이상이어야 합니다.");
        }
        
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("페이지 크기는 1-100 사이여야 합니다.");
        }
        
        try {
            logger.info("=== USER REVIEWS REQUEST ===");
            logger.info("UserId: {}, Page: {}, Size: {}", userId, page, size);
            
            // 실제 서비스 로직 호출 (Repository에서 이미 예외 처리됨)
            List<ReviewEntity> reviews = reviewService.getUserReviews(userId, page, size);
            long totalCount = reviewService.getUserReviewsCount(userId);
            
            // 빈 결과도 정상적으로 처리
            if (reviews == null) {
                reviews = new ArrayList<>();
            }
        
            logger.info("Retrieved {} reviews out of {} total for user: {}", reviews.size(), totalCount, userId);
        
            // 다음 페이지 존재 여부 계산
            boolean hasNext = (page + 1) * size < totalCount;
        
        // 리뷰 데이터를 Map으로 변환 (타입 안전성 확보)
        List<Map<String, Object>> reviewData = reviews.stream()
            .filter(review -> {
                if (review instanceof ReviewEntity) {
                    return true;
                } else {
                    logger.warn("Found non-ReviewEntity object in reviews list: {}", review.getClass().getSimpleName());
                    return false;
                }
            }) // 타입 체크 추가 및 로깅
            .map(review -> (ReviewEntity) review) // 명시적 캐스팅 추가
            .map(review -> {
                Map<String, Object> reviewMap = new HashMap<>();
                reviewMap.put("reviewId", review.getReviewId() != null ? review.getReviewId() : "");
                String reviewId = review.getReviewId();
                // reviewId에 #이 포함되어 있지 않다면, 잘못된 형식으로 간주하고 수정합니다.
                if (reviewId != null && !reviewId.contains("#") && review.getCreatedAt() != null && review.getUserId() != null) {
                    reviewId = review.getCreatedAt().toString() + "#" + review.getUserId();
                }
                reviewMap.put("reviewId", reviewId != null ? reviewId : "");
                reviewMap.put("restaurantId", review.getRestaurantId() != null ? review.getRestaurantId() : "");
                reviewMap.put("restaurantName", review.getRestaurantName() != null ? review.getRestaurantName() : "");
                reviewMap.put("restaurantAddress", review.getRestaurantAddress() != null ? review.getRestaurantAddress() : "");
                reviewMap.put("rating", review.getRating() != null ? review.getRating() : 0);
                reviewMap.put("content", review.getContent() != null ? review.getContent() : "");
                reviewMap.put("imageUrls", review.getImageUrls() != null ? review.getImageUrls() : List.of());
                reviewMap.put("visitDate", review.getVisitDate() != null ? review.getVisitDate() : "");
                reviewMap.put("isVerified", review.getIsVerified() != null ? review.getIsVerified() : false);
                reviewMap.put("createdAt", review.getCreatedAt() != null ? review.getCreatedAt().toString() : "");
                reviewMap.put("updatedAt", review.getUpdatedAt() != null ? review.getUpdatedAt().toString() : "");
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
            logger.error("=== USER REVIEWS REQUEST FAILED ===");
            logger.error("UserId: {}, Page: {}, Size: {}", userId, page, size);
            logger.error("Exception type: {}", e.getClass().getSimpleName());
            logger.error("Exception message: {}", e.getMessage());
            if (e.getCause() != null) {
                logger.error("Cause: {} - {}", e.getCause().getClass().getSimpleName(), e.getCause().getMessage());
            }
            e.printStackTrace();
            
            // GSI 관련 에러인지 확인하여 더 구체적인 응답 제공
            String errorType = "UNKNOWN_ERROR";
            String errorMessage = "사용자 리뷰 조회 중 오류가 발생했습니다";
            
            if (e.getMessage() != null && e.getMessage().contains("UserIdIndex")) {
                errorType = "GSI_ERROR";
                errorMessage = "DynamoDB UserIdIndex GSI 오류로 리뷰를 조회할 수 없습니다";
            } else if (e.getMessage() != null && e.getMessage().contains("AccessDenied")) {
                errorType = "ACCESS_DENIED";
                errorMessage = "DynamoDB 접근 권한이 없습니다";
            }
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", errorMessage);
            errorResponse.put("errorCode", errorType);
            errorResponse.put("data", List.of());
            errorResponse.put("totalCount", 0);
            errorResponse.put("currentPage", page);
            errorResponse.put("totalPages", 0);
            errorResponse.put("hasNext", false);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    // === 미작성 리뷰 관리 API ===
    
    /**
     * 사용자의 미작성 리뷰 목록 조회
     */
    @GetMapping("/pending")
    public ResponseEntity<Map<String, Object>> getPendingReviews(@RequestHeader("x-user-id") String userId) throws Exception {
        
        logger.info("Getting pending reviews for user: {}", userId);
        
        List<PendingReviewEntity> pendingReviews = reviewService.getPendingReviewsByUserId(userId);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", pendingReviews,
            "count", pendingReviews.size()
        ));
    }
    
    /**
     * 미작성 리뷰 삭제 (사용자가 안간 경우)
     */
    @DeleteMapping("/pending/{restaurantId}")
    public ResponseEntity<Map<String, Object>> deletePendingReview(
            @RequestHeader("x-user-id") String userId,
            @PathVariable String restaurantId,
            @RequestParam String scheduledTime) throws Exception {
        
        logger.info("=== PENDING REVIEW DELETION REQUEST ===");
        logger.info("User: {}, Restaurant: {}, ScheduledTime: {}", userId, restaurantId, scheduledTime);
        
        // 삭제 전 사용자의 모든 미작성 리뷰 목록 확인 (디버깅용)
        try {
            List<PendingReviewEntity> allPendingReviews = reviewService.getPendingReviewsByUserId(userId);
            logger.info("User {} has {} pending reviews before deletion:", userId, allPendingReviews.size());
            for (PendingReviewEntity review : allPendingReviews) {
                logger.info("  - RestaurantId: {}, ScheduledTime: {}, CompositeKey: {}", 
                           review.getRestaurantId(), review.getScheduledTime(), 
                           review.getRestaurantIdScheduledTime());
            }
        } catch (Exception e) {
            logger.warn("Failed to fetch user's pending reviews for debugging", e);
        }
        
        boolean success = reviewService.deletePendingReview(userId, restaurantId, scheduledTime);
        
        logger.info("=== PENDING REVIEW DELETION RESULT: {} ===", success);
        
        if (success) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "미작성 리뷰가 삭제되었습니다."
            ));
        } else {
            // 실패 시 더 자세한 정보 제공
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "미작성 리뷰 삭제에 실패했습니다.");
            errorResponse.put("errorCode", "DELETION_FAILED");
            errorResponse.put("details", Map.of(
                "userId", userId,
                "restaurantId", restaurantId, 
                "scheduledTime", scheduledTime,
                "expectedCompositeKey", restaurantId + "#" + scheduledTime
            ));
            
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }
    }
    
    /**
     * 작성된 리뷰 삭제
     */
    @DeleteMapping("/{restaurantId}/{reviewId}")
    public ResponseEntity<Map<String, Object>> deleteReview(
            @RequestHeader("x-user-id") String userId,
            @PathVariable String restaurantId,
            @PathVariable String reviewId) throws Exception {
        
        // URL 디코딩 처리
        String decodedReviewId = java.net.URLDecoder.decode(reviewId, "UTF-8");
        logger.info("Deleting review for user: {}, restaurant: {}, review: {} (decoded: {})", 
                   userId, restaurantId, reviewId, decodedReviewId);
        
        reviewService.deleteReview(restaurantId, decodedReviewId, userId);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "리뷰가 삭제되었습니다."
        ));
    }

    /**
     * 특정 리뷰 상세 조회 (reviewId만 사용)
     */
    @GetMapping("/detail/{reviewId}")
    public ResponseEntity<Map<String, Object>> getReviewByReviewId(
            @RequestHeader("x-user-id") String userId,
            @PathVariable String reviewId) throws Exception {

        logger.info("Getting review detail for review: {}", reviewId);

        Optional<ReviewEntity> reviewOpt = reviewService.getByReviewId(reviewId);

        if (reviewOpt.isEmpty()) {
            throw new IllegalStateException("리뷰를 찾을 수 없습니다.");
        }

        ReviewEntity review = reviewOpt.get();

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
    }

    /**
     * 특정 리뷰 상세 조회
     */
    @GetMapping("/{restaurantId}/{reviewId}")
    public ResponseEntity<Map<String, Object>> getReview(
            @RequestHeader("x-user-id") String userId,
            @PathVariable String restaurantId,
            @PathVariable String reviewId) throws Exception {
        
        // URL 디코딩 처리
        String decodedReviewId = java.net.URLDecoder.decode(reviewId, "UTF-8");
        logger.info("Getting review detail for user: {}, restaurant: {}, review: {} (decoded: {})", 
                   userId, restaurantId, reviewId, decodedReviewId);
        
        Optional<ReviewEntity> reviewOpt = reviewService.getReviewById(restaurantId, decodedReviewId);
        
        if (reviewOpt.isEmpty()) {
            throw new IllegalStateException("리뷰를 찾을 수 없습니다.");
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
            @RequestParam(value = "reviewImages", required = false) List<MultipartFile> reviewImages) throws Exception {
        
        // URL 디코딩 처리
        String decodedReviewId = java.net.URLDecoder.decode(reviewId, "UTF-8");
        logger.info("Updating review for user: {}, restaurant: {}, review: {} (decoded: {})", 
                   userId, restaurantId, reviewId, decodedReviewId);
        
        // 입력값 검증
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("평점은 1-5 사이여야 합니다.");
        }
        
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("리뷰 내용은 필수입니다.");
        }
        
        // 권한 검증 (작성자만 수정 가능)
        Optional<ReviewEntity> existingReview = reviewService.getReviewById(restaurantId, decodedReviewId);
        if (existingReview.isEmpty()) {
            throw new IllegalStateException("수정할 리뷰를 찾을 수 없습니다.");
        }
        
        if (!existingReview.get().getUserId().equals(userId)) {
            throw new SecurityException("리뷰 수정 권한이 없습니다.");
        }
        
        ReviewEntity updatedReview = reviewService.updateReviewWithImages(restaurantId, decodedReviewId, userId, rating, content, reviewImages);
        
        // 완전한 리뷰 데이터 반환
        Map<String, Object> reviewData = new HashMap<>();
        reviewData.put("reviewId", updatedReview.getReviewId());
        reviewData.put("restaurantId", updatedReview.getRestaurantId());
        reviewData.put("restaurantName", updatedReview.getRestaurantName());
        reviewData.put("restaurantAddress", updatedReview.getRestaurantAddress());
        reviewData.put("userId", updatedReview.getUserId());
        reviewData.put("rating", updatedReview.getRating());
        reviewData.put("content", updatedReview.getContent());
        reviewData.put("imageUrls", updatedReview.getImageUrls() != null ? updatedReview.getImageUrls() : List.of());
        reviewData.put("visitDate", updatedReview.getVisitDate());
        reviewData.put("isVerified", updatedReview.getIsVerified() != null ? updatedReview.getIsVerified() : false);
        reviewData.put("createdAt", updatedReview.getCreatedAt().toString());
        reviewData.put("updatedAt", updatedReview.getUpdatedAt().toString());
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "리뷰가 수정되었습니다.",
            "data", reviewData
        ));
    }
    
    /**
     * 특정 미작성 리뷰 상세 조회
     */
    @GetMapping("/pending/{restaurantId}/detail")
    public ResponseEntity<Map<String, Object>> getPendingReviewDetail(
            @RequestHeader("x-user-id") String userId,
            @PathVariable String restaurantId,
            @RequestParam String scheduledTime) throws Exception {
        
        logger.info("Getting pending review detail for user: {}, restaurant: {}", userId, restaurantId);
        
        var pendingReview = reviewService.getPendingReviewDetail(userId, scheduledTime, restaurantId);
        
        if (pendingReview.isPresent()) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", pendingReview.get()
            ));
        } else {
            throw new IllegalStateException("미작성 리뷰를 찾을 수 없습니다.");
        }
    }
    
    /**
     * 헬스 체크 API (서비스 및 Valkey 상태 확인)
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("service", "review-service");
        health.put("timestamp", System.currentTimeMillis());
        
        // DynamoDB 연결 및 UserIdIndex 상태 확인
        try {
            // 테스트용 사용자 ID로 쿼리 시도
            List<ReviewEntity> testResult = reviewService.getUserReviews("test-user-id", 0, 1);
            health.put("dynamodb", Map.of(
                "status", "UP",
                "message", "DynamoDB and UserIdIndex working properly",
                "testQueryResult", testResult.size() + " reviews found"
            ));
        } catch (Exception e) {
            logger.error("DynamoDB UserIdIndex 확인 실패", e);
            health.put("dynamodb", Map.of(
                "status", "DOWN",
                "message", "DynamoDB UserIdIndex issue: " + e.getMessage()
            ));
        }
        
        // Valkey 연결 상태 확인
        try {
            valkeyTemplate.opsForValue().set("health-check", "OK", java.time.Duration.ofSeconds(10));
            String result = (String) valkeyTemplate.opsForValue().get("health-check");
            
            if ("OK".equals(result)) {
                health.put("status", "UP");
                health.put("valkey", Map.of(
                    "status", "UP", 
                    "connection", "ElastiCache Valkey connected successfully"
                ));
            } else {
                health.put("status", "PARTIAL");
                health.put("valkey", Map.of(
                    "status", "DOWN", 
                    "connection", "Valkey read/write test failed"
                ));
            }
        } catch (Exception e) {
            logger.error("Valkey 연결 확인 실패", e);
            health.put("status", "PARTIAL");
            health.put("valkey", Map.of(
                "status", "DOWN", 
                "connection", "Valkey connection failed: " + e.getMessage()
            ));
        }
        
        return ResponseEntity.ok(health);
    }
}