package com.mapzip.review.service;

import com.mapzip.review.dto.OcrResultDto;
import com.mapzip.review.entity.ReviewEntity;
import com.mapzip.review.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReviewService {
    
    private static final Logger logger = LoggerFactory.getLogger(ReviewService.class);
    
    private final ReviewRepository reviewRepository;
    private final OcrService ocrService;
    private final S3Service s3Service;
    
    @Autowired
    public ReviewService(ReviewRepository reviewRepository, 
                        OcrService ocrService, 
                        S3Service s3Service) {
        this.reviewRepository = reviewRepository;
        this.ocrService = ocrService;
        this.s3Service = s3Service;
    }
    
    public ReviewCreateResult createReview(String userId, String restaurantId, String restaurantName, 
                                         String restaurantAddress, int rating, String content,
                                         List<byte[]> receiptImages, List<byte[]> reviewImages) {
        try {
            // OCR 검증 수행
            OcrResultDto ocrResult = null;
            boolean isVerified = false;
            
            if (receiptImages != null && !receiptImages.isEmpty()) {
                // 첫 번째 영수증 이미지로 OCR 수행
                ocrResult = ocrService.processReceiptImage(
                    receiptImages.get(0), restaurantName, restaurantAddress);
                isVerified = ocrResult.isValid();
                logger.info("OCR verification result for user {}: {}", userId, isVerified);

                if (!isVerified) {
                    return new ReviewCreateResult(null, ocrResult, false, "영수증 검증에 실패하여 리뷰를 생성할 수 없습니다.");
                }
            }
            
            // 리뷰 이미지 S3 업로드
            List<String> imageUrls = new ArrayList<>();
            if (reviewImages != null) {
                for (byte[] imageData : reviewImages) {
                    try {
                        String imageUrl = s3Service.uploadImage(imageData, "image/jpeg", userId);
                        imageUrls.add(imageUrl);
                    } catch (Exception e) {
                        logger.error("Failed to upload review image for user {}", userId, e);
                    }
                }
            }
            
            // 리뷰 엔티티 생성
            ReviewEntity review = new ReviewEntity();
            review.setRestaurantId(restaurantId);
            review.setReviewId(UUID.randomUUID().toString()); // 고유한 리뷰 ID 생성
            review.setUserId(userId);
            review.setRestaurantName(restaurantName);
            review.setRestaurantAddress(restaurantAddress);
            review.setRating(rating);
            review.setContent(content);
            review.setImageUrls(imageUrls);
            review.setIsVerified(isVerified);
            review.setCreatedAt(Instant.now());
            review.setUpdatedAt(Instant.now());
            
            // 방문 날짜 설정 (OCR에서 추출되었다면 사용, 아니면 현재 날짜)
            if (ocrResult != null && ocrResult.getVisitDate() != null && !ocrResult.getVisitDate().isEmpty()) {
                review.setVisitDate(ocrResult.getVisitDate());
            } else {
                review.setVisitDate(Instant.now().toString().split("T")[0]); // yyyy-MM-dd 형식
            }
            
            // 리뷰 저장
            ReviewEntity savedReview = reviewRepository.save(review);
            
            return new ReviewCreateResult(savedReview, ocrResult, true, "리뷰가 성공적으로 작성되었습니다.");
            
        } catch (Exception e) {
            logger.error("Failed to create review for user {}", userId, e);
            return new ReviewCreateResult(null, null, false, "리뷰 작성 실패: " + e.getMessage());
        }
    }
    
    public List<ReviewEntity> getUserReviews(String userId, int page, int size) {
        return reviewRepository.findByUserId(userId);
    }
    
    public List<ReviewEntity> getRestaurantReviews(String restaurantId, int page, int size) {
        return reviewRepository.findByRestaurantId(restaurantId, page, size);
    }
    
    public Optional<ReviewEntity> getReview(String restaurantId, String reviewId) {
        return reviewRepository.findByRestaurantIdAndReviewId(restaurantId, reviewId);
    }
    
    public ReviewEntity updateReview(String restaurantId, String reviewId, String userId, 
                                   int rating, String content, List<String> imageUrls) {
        Optional<ReviewEntity> existingReview = reviewRepository.findByRestaurantIdAndReviewId(restaurantId, reviewId);
        
        if (existingReview.isEmpty()) {
            throw new RuntimeException("리뷰를 찾을 수 없습니다.");
        }
        
        ReviewEntity review = existingReview.get();
        
        // 작성자 검증
        if (!review.getUserId().equals(userId)) {
            throw new RuntimeException("리뷰 수정 권한이 없습니다.");
        }
        
        // 리뷰 업데이트
        review.setRating(rating);
        review.setContent(content);
        review.setImageUrls(imageUrls);
        review.setUpdatedAt(Instant.now());
        
        return reviewRepository.save(review);
    }
    
    public void deleteReview(String restaurantId, String reviewId, String userId) {
        Optional<ReviewEntity> existingReview = reviewRepository.findByRestaurantIdAndReviewId(restaurantId, reviewId);
        
        if (existingReview.isEmpty()) {
            throw new RuntimeException("리뷰를 찾을 수 없습니다.");
        }
        
        ReviewEntity review = existingReview.get();
        
        // 작성자 검증
        if (!review.getUserId().equals(userId)) {
            throw new RuntimeException("리뷰 삭제 권한이 없습니다.");
        }
        
        reviewRepository.deleteByRestaurantIdAndReviewId(restaurantId, reviewId);
    }
    
    public long getRestaurantReviewCount(String restaurantId) {
        return reviewRepository.countByRestaurantId(restaurantId);
    }
    
    public double getRestaurantAverageRating(String restaurantId) {
        return reviewRepository.getAverageRatingByRestaurantId(restaurantId);
    }
    
    public OcrResultDto verifyReceipt(byte[] receiptImage, String expectedRestaurantName, String expectedAddress) {
        return ocrService.processReceiptImage(receiptImage, expectedRestaurantName, expectedAddress);
    }
    
    // 내부 클래스: 리뷰 생성 결과
    public static class ReviewCreateResult {
        private final ReviewEntity review;
        private final OcrResultDto ocrResult;
        private final boolean success;
        private final String message;
        
        public ReviewCreateResult(ReviewEntity review, OcrResultDto ocrResult, boolean success, String message) {
            this.review = review;
            this.ocrResult = ocrResult;
            this.success = success;
            this.message = message;
        }
        
        public ReviewEntity getReview() { return review; }
        public OcrResultDto getOcrResult() { return ocrResult; }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}