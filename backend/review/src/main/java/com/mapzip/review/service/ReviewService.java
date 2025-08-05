package com.mapzip.review.service;

import com.mapzip.review.dto.OcrResultDto;
import com.mapzip.review.entity.ReviewEntity;
import com.mapzip.review.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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
    
    @Caching(evict = {
        @CacheEvict(value = "userReviews", allEntries = true),
        @CacheEvict(value = "restaurantReviews", allEntries = true),
        @CacheEvict(value = "reviewStats", allEntries = true)
    })
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
    
    @Cacheable(value = "userReviews", key = "#userId + '_' + #page + '_' + #size")
    public List<ReviewEntity> getUserReviews(String userId, int page, int size) {
        logger.info("Fetching user reviews from database for userId: {}, page: {}, size: {}", userId, page, size);
        return reviewRepository.findByUserId(userId);
    }
    
    @Cacheable(value = "restaurantReviews", key = "#restaurantId + '_' + #page + '_' + #size")
    public List<ReviewEntity> getRestaurantReviews(String restaurantId, int page, int size) {
        logger.info("Fetching restaurant reviews from database for restaurantId: {}, page: {}, size: {}", restaurantId, page, size);
        return reviewRepository.findByRestaurantId(restaurantId, page, size);
    }
    
    public Optional<ReviewEntity> getReview(String restaurantId, String reviewId) {
        return reviewRepository.findByRestaurantIdAndReviewId(restaurantId, reviewId);
    }
    
    @Caching(evict = {
        @CacheEvict(value = "userReviews", allEntries = true),
        @CacheEvict(value = "restaurantReviews", allEntries = true),
        @CacheEvict(value = "reviewStats", allEntries = true)
    })
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
    
    @Caching(evict = {
        @CacheEvict(value = "userReviews", allEntries = true),
        @CacheEvict(value = "restaurantReviews", allEntries = true),
        @CacheEvict(value = "reviewStats", allEntries = true)
    })
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
    
    @Cacheable(value = "reviewStats", key = "'count_' + #restaurantId")
    public long getRestaurantReviewCount(String restaurantId) {
        logger.info("Fetching review count from database for restaurantId: {}", restaurantId);
        return reviewRepository.countByRestaurantId(restaurantId);
    }
    
    @Cacheable(value = "reviewStats", key = "'avg_rating_' + #restaurantId")
    public double getRestaurantAverageRating(String restaurantId) {
        logger.info("Fetching average rating from database for restaurantId: {}", restaurantId);
        return reviewRepository.getAverageRatingByRestaurantId(restaurantId);
    }
    
    @Cacheable(value = "ocrResults", key = "T(java.util.Arrays).hashCode(#receiptImage) + '_' + #expectedRestaurantName")
    public OcrResultDto verifyReceipt(byte[] receiptImage, String expectedRestaurantName, String expectedAddress) {
        logger.info("Processing OCR for restaurant: {}", expectedRestaurantName);
        return ocrService.processReceiptImage(receiptImage, expectedRestaurantName, expectedAddress);
    }
    
    // 추천 서버용 리뷰 데이터 조회
    public List<ReviewEntity> getReviewsForRecommendation(String area, String category, int page, int size) {
        // 지역이나 카테고리 기반으로 리뷰를 조회하는 로직
        // 현재는 간단한 구현으로 모든 리뷰 중에서 페이징 처리된 결과를 반환
        // 실제로는 GSI를 추가하거나 별도의 검색 로직이 필요할 수 있음
        
        // 임시 구현: 모든 리뷰를 스캔하여 필터링 (성능상 권장하지 않음, 추후 개선 필요)
        logger.warn("getReviewsForRecommendation - 임시 구현 사용 중. 성능 최적화 필요.");
        
        // TODO: 실제 구현 시에는 아래와 같은 방법들을 고려해야 함:
        // 1. DynamoDB GSI를 추가하여 지역/카테고리별 인덱스 구성
        // 2. ElasticSearch 연동하여 복잡한 검색 쿼리 지원
        // 3. 캐싱 레이어 추가하여 자주 요청되는 데이터 성능 개선
        
        return List.of(); // 현재는 빈 리스트 반환
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