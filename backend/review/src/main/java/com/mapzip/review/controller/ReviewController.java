package com.mapzip.review.controller;

import com.mapzip.review.dto.OcrResultDto;
import com.mapzip.review.service.ReviewService;
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
@RequestMapping("/api/reviews")
@CrossOrigin(origins = "*")
public class ReviewController {
    
    private static final Logger logger = LoggerFactory.getLogger(ReviewController.class);
    
    private final ReviewService reviewService;
    
    @Autowired
    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }
    
    /**
     * 영수증 OCR 검증 API
     * 프론트엔드에서 이미지 업로드 후 OCR 검증 결과를 받는 용도
     */
    @PostMapping(value = "/verify-receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> verifyReceipt(
            @RequestParam("receiptImage") MultipartFile receiptImage,
            @RequestParam("expectedRestaurantName") String expectedRestaurantName,
            @RequestParam("expectedAddress") String expectedAddress) {
        
        try {
            logger.info("Verifying receipt for restaurant: {}", expectedRestaurantName);
            
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
            @RequestParam("restaurantId") String restaurantId,
            @RequestParam("restaurantName") String restaurantName,
            @RequestParam("restaurantAddress") String restaurantAddress,
            @RequestParam("rating") int rating,
            @RequestParam("content") String content,
            @RequestParam(value = "receiptImages", required = false) List<MultipartFile> receiptImages,
            @RequestParam(value = "reviewImages", required = false) List<MultipartFile> reviewImages) {
        
        try {
            // 임시로 고정된 userId 사용 (실제로는 Gateway에서 passport 헤더로 전달받아야 함)
            String userId = "user-1";
            
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
            
            // 리뷰 생성
            ReviewService.ReviewCreateResult result = reviewService.createReview(
                userId, restaurantId, restaurantName, restaurantAddress, 
                rating, content, receiptImageBytes, reviewImageBytes);
            
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
    
    /**
     * 헬스 체크 API
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "review-service"));
    }
}