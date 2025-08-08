package com.mapzip.review.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapzip.review.dto.OcrResultDto;
import com.mapzip.review.entity.ReviewEntity;
import com.mapzip.review.entity.PendingReviewEntity;
import com.mapzip.review.grpc.HeaderInterceptor;
import com.mapzip.review.grpc.ReviewProto;
import com.mapzip.review.repository.ReviewRepository;
import com.mapzip.review.repository.PendingReviewRepository;
import io.grpc.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReviewService {
    
    private static final Logger logger = LoggerFactory.getLogger(ReviewService.class);
    
    private final ReviewRepository reviewRepository;
    private final PendingReviewRepository pendingReviewRepository;
    private final OcrService ocrService;
    private final S3Service s3Service;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public ReviewService(ReviewRepository reviewRepository, 
                        PendingReviewRepository pendingReviewRepository,
                        OcrService ocrService, 
                        S3Service s3Service,
                        RedisTemplate<String, Object> redisTemplate,
                        ObjectMapper objectMapper) {
        this.reviewRepository = reviewRepository;
        this.pendingReviewRepository = pendingReviewRepository;
        this.ocrService = ocrService;
        this.s3Service = s3Service;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }
    
    @Caching(evict = {
        @CacheEvict(value = "userReviews", allEntries = true),
        @CacheEvict(value = "restaurantReviews", allEntries = true),
        @CacheEvict(value = "reviewStats", allEntries = true)
    })
    public ReviewCreateResult createReview(String userId, String restaurantId, String restaurantName, 
                                         String restaurantAddress, int rating, String content,
                                         List<byte[]> receiptImages, List<byte[]> reviewImages, String visitDate) {
        try {
            // 사용자 인증 검증
            validateUserAuthentication(userId);
            
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
                
                // OCR 날짜와 실제 방문 날짜 비교 검증
                if (visitDate != null && ocrResult.getVisitDate() != null) {
                    boolean isDateValid = ocrService.validateVisitDate(ocrResult.getVisitDate(), visitDate);
                    if (!isDateValid) {
                        return new ReviewCreateResult(null, ocrResult, false, 
                            "영수증 날짜가 방문 날짜와 일치하지 않습니다. 리뷰 작성이 차단되었습니다.");
                    }
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
            review.setUserId(userId);
            review.setRestaurantName(restaurantName);
            review.setRestaurantAddress(restaurantAddress);
            review.setRating(rating);
            review.setContent(content);
            review.setImageUrls(imageUrls);
            review.setIsVerified(isVerified);
            review.setCreatedAt(Instant.now());
            review.setUpdatedAt(Instant.now());
            
            // DynamoDB 복합키 생성
            review.generateCompositeKey();
            
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
        // 사용자 인증 검증
        validateUserAuthentication(userId);
        
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
        // 사용자 인증 검증
        validateUserAuthentication(userId);
        
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
    
    /**
     * 추천 서버용 리뷰 데이터 조회
     * @param area 지역 필터 (예: "강남구", "서초구", "전체" 등)
     * @param category 카테고리 필터 (예: "한식", "중식", "전체" 등)
     * @param page 페이지 번호 (0부터 시작)
     * @param size 페이지 크기
     * @return 추천용 고품질 리뷰 목록
     */
    @Cacheable(value = "recommendationReviews", key = "#area + '_' + #category + '_' + #page + '_' + #size")
    public List<ReviewEntity> getReviewsForRecommendation(String area, String category, int page, int size) {
        logger.info("Fetching reviews for recommendation - area: {}, category: {}, page: {}, size: {}", 
                   area, category, page, size);
        
        try {
            List<ReviewEntity> reviews;
            
            // 지역 필터링 로직
            if (area != null && !area.isEmpty() && !area.equals("전체")) {
                // 주소 패턴으로 검색 (예: "강남구" 포함된 주소)
                reviews = reviewRepository.findReviewsByAddressPattern(area, page, size);
                logger.debug("Found {} reviews for area: {}", reviews.size(), area);
            } else {
                // 전체 지역: 고품질 리뷰만 조회 (OCR 검증 + 평점 3점 이상)
                reviews = reviewRepository.findHighQualityReviewsForRecommendation(page, size);
                logger.debug("Found {} high-quality reviews", reviews.size());
            }
            
            // 카테고리 필터링 (식당명이나 주소에서 카테고리 키워드 매칭)
            if (category != null && !category.isEmpty() && !category.equals("전체")) {
                reviews = filterReviewsByCategory(reviews, category);
                logger.debug("After category filtering ({}): {} reviews", category, reviews.size());
            }
            
            // 추천용 데이터만 추출 (AI가 분석하기 좋은 형태로 가공)
            reviews = prepareReviewsForAI(reviews);
            
            logger.info("Successfully fetched {} reviews for recommendation", reviews.size());
            return reviews;
            
        } catch (Exception e) {
            logger.error("Error fetching reviews for recommendation", e);
            // 에러 발생 시 빈 리스트 반환하여 추천 서비스 중단 방지
            return List.of();
        }
    }
    
    /**
     * 특정 식당 ID들의 리뷰를 조회 (추천 서버에서 Kakao API 결과와 매칭할 때 사용)
     */
    public List<ReviewEntity> getReviewsByRestaurantIds(List<String> restaurantIds) {
        logger.info("Fetching reviews for {} restaurants", restaurantIds.size());
        
        try {
            // 각 식당별로 최대 5개의 최신 리뷰 조회
            List<ReviewEntity> reviews = reviewRepository.findRecentReviewsByRestaurantIds(restaurantIds, 5);
            
            // AI 분석용으로 데이터 가공
            reviews = prepareReviewsForAI(reviews);
            
            logger.info("Found {} reviews for {} restaurants", reviews.size(), restaurantIds.size());
            return reviews;
            
        } catch (Exception e) {
            logger.error("Error fetching reviews by restaurant IDs", e);
            return List.of();
        }
    }
    
    /**
     * 카테고리별 리뷰 필터링
     * 실제 운영에서는 식당 카테고리 정보가 별도 테이블에 있어야 하지만,
     * 현재는 식당명/주소에서 키워드 매칭으로 처리
     */
    private List<ReviewEntity> filterReviewsByCategory(List<ReviewEntity> reviews, String category) {
        // 카테고리별 키워드 매핑
        List<String> keywords = getCategoryKeywords(category);
        
        return reviews.stream()
                .filter(review -> {
                    String restaurantInfo = (review.getRestaurantName() + " " + review.getRestaurantAddress()).toLowerCase();
                    return keywords.stream().anyMatch(keyword -> restaurantInfo.contains(keyword.toLowerCase()));
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 카테고리별 검색 키워드 반환
     */
    private List<String> getCategoryKeywords(String category) {
        return switch (category.toLowerCase()) {
            case "한식", "korean" -> List.of("한식", "김치", "갈비", "불고기", "비빔밥", "한정식");
            case "중식", "chinese" -> List.of("중식", "중국", "짜장면", "짬뽕", "탕수육", "마라");
            case "일식", "japanese" -> List.of("일식", "일본", "초밥", "라멘", "우동", "사시미");
            case "양식", "western" -> List.of("양식", "파스타", "피자", "스테이크", "샐러드");
            case "카페", "cafe" -> List.of("카페", "커피", "디저트", "브런치", "베이커리");
            case "치킨", "chicken" -> List.of("치킨", "닭", "프라이드", "양념");
            case "피자", "pizza" -> List.of("피자", "pizza");
            case "햄버거", "burger" -> List.of("햄버거", "버거", "burger");
            default -> List.of(category); // 기본적으로 카테고리명 자체를 키워드로 사용
        };
    }
    
    /**
     * AI 분석용 리뷰 데이터 가공
     * - 너무 짧은 리뷰 제외
     * - 의미있는 내용만 선별
     * - 최신 리뷰 우선
     */
    private List<ReviewEntity> prepareReviewsForAI(List<ReviewEntity> reviews) {
        return reviews.stream()
                .filter(review -> review.getContent() != null && review.getContent().length() >= 10) // 최소 10자 이상
                .filter(review -> review.getRating() != null && review.getRating() >= 2) // 1점 리뷰 제외
                .filter(review -> !isSpamReview(review.getContent())) // 스팸 리뷰 제외
                .sorted((r1, r2) -> {
                    // 정렬 우선순위: 1) 검증된 리뷰 2) 최신순 3) 높은 평점순
                    // NPE 방지를 위한 안전한 null 체크
                    boolean r1Verified = Boolean.TRUE.equals(r1.getIsVerified());
                    boolean r2Verified = Boolean.TRUE.equals(r2.getIsVerified());
                    int verifiedCompare = Boolean.compare(r2Verified, r1Verified);
                    if (verifiedCompare != 0) return verifiedCompare;
                    
                    int timeCompare = r2.getCreatedAt().compareTo(r1.getCreatedAt());
                    if (timeCompare != 0) return timeCompare;
                    
                    int r1Rating = r1.getRating() != null ? r1.getRating() : 0;
                    int r2Rating = r2.getRating() != null ? r2.getRating() : 0;
                    return Integer.compare(r2Rating, r1Rating);
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 간단한 스팸 리뷰 필터링
     * 실제 운영에서는 ML 모델이나 더 정교한 필터링 필요
     */
    private boolean isSpamReview(String content) {
        if (content == null || content.trim().isEmpty()) return true;
        
        String lowerContent = content.toLowerCase();
        
        // 스팸 패턴 체크
        List<String> spamPatterns = List.of(
            "광고", "홍보", "이벤트", "쿠폰", "할인", "http", "www", "링크",
            "맛없", "별로", "최악", "돈아까", "비추"  // 극단적 부정 리뷰도 제외
        );
        
        long spamCount = spamPatterns.stream()
                .mapToLong(pattern -> content.toLowerCase().split(pattern).length - 1)
                .sum();
                
        return spamCount > 2; // 스팸 키워드가 3개 이상이면 스팸으로 판단
    }
    
    /**
     * 사용자 인증 검증 메서드
     * gRPC Context에서 받은 사용자 ID와 요청의 사용자 ID가 일치하는지 확인
     */
    private void validateUserAuthentication(String requestedUserId) {
        String authenticatedUserId = HeaderInterceptor.USER_ID_CONTEXT_KEY.get();
        
        if (authenticatedUserId == null || authenticatedUserId.isEmpty()) {
            throw new SecurityException("인증되지 않은 사용자입니다.");
        }
        
        if (!authenticatedUserId.equals(requestedUserId)) {
            throw new SecurityException("다른 사용자의 리뷰에 접근할 수 없습니다.");
        }
        
        logger.debug("User authentication validated for userId: {}", authenticatedUserId);
    }
    
    /**
     * 추천 서버에서 보낸 식당 정보를 Valkey에 저장
     * @param userId 사용자 ID
     * @param places 식당 정보 목록
     */
    public void storePlacesForReview(String userId, List<ReviewProto.ReviewPlaceInfo> places) {
        logger.info("Storing {} places for user: {}", places.size(), userId);
        
        try {
            String key = "places_for_review:" + userId;
            
            // 기존 데이터 삭제 후 새로운 데이터 저장
            redisTemplate.delete(key);
            
            // 각 식당 정보를 JSON으로 변환하여 저장
            for (int i = 0; i < places.size(); i++) {
                ReviewProto.ReviewPlaceInfo place = places.get(i);
                String placeJson = String.format(
                    "{\"id\":\"%s\",\"placeName\":\"%s\",\"addressName\":\"%s\",\"placeUrl\":\"%s\",\"scheduledTime\":\"%s\"}",
                    place.getId(),
                    place.getPlaceName().replace("\"", "\\\""),
                    place.getAddressName().replace("\"", "\\\""),
                    place.getPlaceUrl(),
                    place.getScheduledTime()
                );
                
                redisTemplate.opsForList().rightPush(key, placeJson);
            }
            
            // TTL 설정 (7일)
            redisTemplate.expire(key, java.time.Duration.ofDays(7));
            
            logger.info("Successfully stored {} places for user: {}", places.size(), userId);
            
        } catch (Exception e) {
            logger.error("Failed to store places for user: {}", userId, e);
            throw new RuntimeException("식당 정보 저장 실패: " + e.getMessage());
        }
    }
    
    /**
     * 사용자가 리뷰 작성 가능한 식당 목록 조회
     * @param userId 사용자 ID
     * @return 식당 정보 목록
     */
    public List<ReviewProto.ReviewPlaceInfo> getPlacesForReview(String userId) {
        logger.info("Getting places for review for user: {}", userId);
        
        try {
            String key = "places_for_review:" + userId;
            List<Object> placesData = redisTemplate.opsForList().range(key, 0, -1);
            
            if (placesData == null || placesData.isEmpty()) {
                logger.info("No places found for user: {}", userId);
                return List.of();
            }
            
            List<ReviewProto.ReviewPlaceInfo> places = new ArrayList<>();
            for (Object placeData : placesData) {
                try {
                    String placeJson = placeData.toString();
                    // 간단한 JSON 파싱 (실제로는 Jackson 등을 사용하는 것이 좋음)
                    String id = extractJsonValue(placeJson, "id");
                    String placeName = extractJsonValue(placeJson, "placeName");
                    String addressName = extractJsonValue(placeJson, "addressName");
                    String placeUrl = extractJsonValue(placeJson, "placeUrl");
                    String scheduledTime = extractJsonValue(placeJson, "scheduledTime");
                    
                    ReviewProto.ReviewPlaceInfo place = ReviewProto.ReviewPlaceInfo.newBuilder()
                            .setId(id)
                            .setPlaceName(placeName)
                            .setAddressName(addressName)
                            .setPlaceUrl(placeUrl)
                            .setScheduledTime(scheduledTime)
                            .build();
                    
                    places.add(place);
                } catch (Exception e) {
                    logger.warn("Failed to parse place data: {}", placeData, e);
                }
            }
            
            logger.info("Retrieved {} places for user: {}", places.size(), userId);
            return places;
            
        } catch (Exception e) {
            logger.error("Failed to get places for user: {}", userId, e);
            return List.of();
        }
    }
    
    /**
     * 추천 서버용 리뷰 요약 정보 제공
     * @param restaurantIds 식당 ID 목록
     * @return 식당별 리뷰 요약
     */
    public List<ReviewProto.RestaurantReviewSummary> getReviewSummaryForRecommendation(List<String> restaurantIds) {
        logger.info("Getting review summary for {} restaurants", restaurantIds.size());
        
        List<ReviewProto.RestaurantReviewSummary> summaries = new ArrayList<>();
        
        for (String restaurantId : restaurantIds) {
            try {
                // 평균 평점과 총 리뷰 수 조회
                double averageRating = getRestaurantAverageRating(restaurantId);
                long totalReviews = getRestaurantReviewCount(restaurantId);
                
                // 상위 3개 리뷰 조회 (평점 4점 이상, 최신순)
                List<ReviewEntity> topReviews = reviewRepository.findByRestaurantId(restaurantId, 0, 10)
                        .stream()
                        .filter(review -> review.getRating() != null && review.getRating() >= 4)
                        .filter(review -> review.getContent() != null && review.getContent().length() >= 10)
                        .limit(3)
                        .collect(Collectors.toList());
                
                List<String> topReviewContents = topReviews.stream()
                        .map(ReviewEntity::getContent)
                        .collect(Collectors.toList());
                
                ReviewProto.RestaurantReviewSummary summary = ReviewProto.RestaurantReviewSummary.newBuilder()
                        .setRestaurantId(restaurantId)
                        .setAverageRating(averageRating)
                        .setTotalReviews((int) totalReviews)
                        .addAllTopReviews(topReviewContents)
                        .build();
                
                summaries.add(summary);
                
            } catch (Exception e) {
                logger.warn("Failed to get review summary for restaurant: {}", restaurantId, e);
                // 에러가 발생한 경우 기본값으로 추가
                ReviewProto.RestaurantReviewSummary defaultSummary = ReviewProto.RestaurantReviewSummary.newBuilder()
                        .setRestaurantId(restaurantId)
                        .setAverageRating(0.0)
                        .setTotalReviews(0)
                        .build();
                
                summaries.add(defaultSummary);
            }
        }
        
        logger.info("Generated review summaries for {} restaurants", summaries.size());
        return summaries;
    }
    
    /**
     * 간단한 JSON 값 추출 유틸리티 메서드
     * 실제 운영에서는 Jackson ObjectMapper 사용 권장
     */
    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\":\"([^\"]*)\""; 
        java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher matcher = regex.matcher(json);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }
    
    // === 미작성 리뷰 관리 메서드들 ===
    
    /**
     * 추천서버에서 선택된 식당들을 미작성 리뷰로 저장
     */
    public boolean savePendingReviews(String userId, List<ReviewProto.ReviewPlaceInfo> places) {
        logger.info("Saving {} pending reviews for user: {}", places.size(), userId);
        
        try {
            List<PendingReviewEntity> pendingReviews = places.stream()
                    .map(place -> {
                        PendingReviewEntity entity = new PendingReviewEntity();
                        entity.setUserId(userId);
                        entity.setRestaurantId(place.getId());
                        entity.setPlaceName(place.getPlaceName());
                        entity.setAddressName(place.getAddressName());
                        entity.setPlaceUrl(place.getPlaceUrl());
                        entity.setScheduledTime(place.getScheduledTime());
                        return entity;
                    })
                    .collect(Collectors.toList());
            
            return pendingReviewRepository.saveBatch(pendingReviews);
            
        } catch (Exception e) {
            logger.error("Failed to save pending reviews for user: {}", userId, e);
            return false;
        }
    }
    
    /**
     * 사용자의 미작성 리뷰 목록 조회
     */
    public List<PendingReviewEntity> getPendingReviewsByUserId(String userId) {
        logger.info("Getting pending reviews for user: {}", userId);
        return pendingReviewRepository.findIncompleteByUserId(userId);
    }
    
    /**
     * 미작성 리뷰 삭제 (사용자가 안간 경우)
     */
    public boolean deletePendingReview(String userId, String scheduledTime, String restaurantId) {
        logger.info("Deleting pending review for user: {}, restaurant: {}", userId, restaurantId);
        
        String compositeKey = scheduledTime + "#" + restaurantId;
        return pendingReviewRepository.delete(userId, compositeKey);
    }
    
    /**
     * 리뷰 작성 완료 시 미작성 리뷰를 완료 처리
     */
    public boolean markPendingReviewAsCompleted(String userId, String restaurantId, String scheduledTime) {
        logger.info("Marking pending review as completed for user: {}, restaurant: {}", userId, restaurantId);
        
        String compositeKey = scheduledTime + "#" + restaurantId;
        return pendingReviewRepository.markAsCompleted(userId, compositeKey);
    }
    
    /**
     * 특정 미작성 리뷰 상세 조회
     */
    public Optional<PendingReviewEntity> getPendingReviewDetail(String userId, String scheduledTime, String restaurantId) {
        logger.info("Getting pending review detail for user: {}, restaurant: {}", userId, restaurantId);
        
        String compositeKey = scheduledTime + "#" + restaurantId;
        return pendingReviewRepository.findByUserIdAndCompositeKey(userId, compositeKey);
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