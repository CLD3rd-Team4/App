package com.mapzip.review.grpc;

import com.google.protobuf.ByteString;
import com.mapzip.review.dto.OcrResultDto;
import com.mapzip.review.entity.ReviewEntity;
import com.mapzip.review.entity.PendingReviewEntity;
import com.mapzip.review.service.ReviewService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@GrpcService
public class ReviewGrpcService extends ReviewServiceGrpc.ReviewServiceImplBase {
    
    private static final Logger logger = LoggerFactory.getLogger(ReviewGrpcService.class);
    
    private final ReviewService reviewService;
    
    public ReviewGrpcService(ReviewService reviewService) {
        this.reviewService = reviewService;
    }
    
    @Override
    public void createReview(ReviewProto.CreateReviewRequest request,
                           StreamObserver<ReviewProto.CreateReviewResponse> responseObserver) {
        try {
            logger.info("Creating review for restaurant: {}", request.getRestaurantId());
            
            String userId = HeaderInterceptor.USER_ID_CONTEXT_KEY.get();
            if (userId == null || userId.isEmpty()) {
                responseObserver.onError(new StatusRuntimeException(Status.UNAUTHENTICATED.withDescription("User ID is missing")));
                return;
            }
            
            // 영수증 이미지 변환
            List<byte[]> receiptImages = request.getReceiptImagesList().stream()
                    .map(ByteString::toByteArray)
                    .collect(Collectors.toList());
            
            // 리뷰 이미지 변환
            List<byte[]> reviewImages = request.getReviewImagesList().stream()
                    .map(ByteString::toByteArray)
                    .collect(Collectors.toList());
            
            // 리뷰 생성
            ReviewService.ReviewCreateResult result = reviewService.createReview(
                    userId,
                    request.getRestaurantId(),
                    request.getRestaurantName(),
                    request.getRestaurantAddress(),
                    request.getRating(),
                    request.getContent(),
                    receiptImages,
                    reviewImages,
                    request.getVisitDate()
            );
            
            // 응답 생성
            ReviewProto.CreateReviewResponse.Builder responseBuilder = 
                    ReviewProto.CreateReviewResponse.newBuilder()
                            .setSuccess(result.isSuccess())
                            .setMessage(result.getMessage());
            
            if (result.getReview() != null) {
                responseBuilder.setReview(convertToProtoReview(result.getReview()));
            }
            
            if (result.getOcrResult() != null) {
                responseBuilder.setOcrResult(convertToProtoOcrResult(result.getOcrResult()));
            }
            
            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            logger.error("Error creating review", e);
            responseObserver.onError(e);
        }
    }
    
    @Override
    public void getUserReviews(ReviewProto.GetUserReviewsRequest request,
                             StreamObserver<ReviewProto.GetUserReviewsResponse> responseObserver) {
        try {
            String authenticatedUserId = HeaderInterceptor.USER_ID_CONTEXT_KEY.get();
            if (authenticatedUserId == null || authenticatedUserId.isEmpty()) {
                responseObserver.onError(new StatusRuntimeException(Status.UNAUTHENTICATED.withDescription("User ID is missing")));
                return;
            }
            
            // 요청한 사용자 ID와 인증된 사용자 ID가 일치하는지 확인
            if (!authenticatedUserId.equals(request.getUserId())) {
                responseObserver.onError(new StatusRuntimeException(Status.PERMISSION_DENIED.withDescription("Access denied: Cannot access other user's reviews")));
                return;
            }
            
            List<ReviewEntity> reviews = reviewService.getUserReviews(
                    request.getUserId(), request.getPage(), request.getSize());
            
            ReviewProto.GetUserReviewsResponse response = ReviewProto.GetUserReviewsResponse.newBuilder()
                    .addAllReviews(reviews.stream()
                            .map(this::convertToProtoReview)
                            .collect(Collectors.toList()))
                    .setTotalCount(reviews.size())
                    .setCurrentPage(request.getPage())
                    .setHasNext(false) // 간단한 구현
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            logger.error("Error getting user reviews", e);
            responseObserver.onError(e);
        }
    }
    
    @Override
    public void getRestaurantReviews(ReviewProto.GetRestaurantReviewsRequest request,
                                   StreamObserver<ReviewProto.GetRestaurantReviewsResponse> responseObserver) {
        try {
            List<ReviewEntity> reviews = reviewService.getRestaurantReviews(
                    request.getRestaurantId(), request.getPage(), request.getSize());
            
            long totalCount = reviewService.getRestaurantReviewCount(request.getRestaurantId());
            double averageRating = reviewService.getRestaurantAverageRating(request.getRestaurantId());
            
            ReviewProto.GetRestaurantReviewsResponse response = ReviewProto.GetRestaurantReviewsResponse.newBuilder()
                    .addAllReviews(reviews.stream()
                            .map(this::convertToProtoReview)
                            .collect(Collectors.toList()))
                    .setTotalCount((int) totalCount)
                    .setCurrentPage(request.getPage())
                    .setHasNext((request.getPage() + 1) * request.getSize() < totalCount)
                    .setAverageRating(averageRating)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            logger.error("Error getting restaurant reviews", e);
            responseObserver.onError(e);
        }
    }
    
    @Override
    public void getReview(ReviewProto.GetReviewRequest request,
                        StreamObserver<ReviewProto.GetReviewResponse> responseObserver) {
        try {
            String authenticatedUserId = HeaderInterceptor.USER_ID_CONTEXT_KEY.get();
            // 리뷰 조회는 누구나 가능하므로 인증 체크만 수행 (로그인 여부 확인)
            if (authenticatedUserId == null || authenticatedUserId.isEmpty()) {
                responseObserver.onError(new StatusRuntimeException(Status.UNAUTHENTICATED.withDescription("User ID is missing")));
                return;
            }
            
            Optional<ReviewEntity> review = reviewService.getReview(
                    request.getRestaurantId(), request.getReviewId());
            
            ReviewProto.GetReviewResponse.Builder responseBuilder = 
                    ReviewProto.GetReviewResponse.newBuilder();
            
            if (review.isPresent()) {
                responseBuilder.setReview(convertToProtoReview(review.get()))
                        .setSuccess(true)
                        .setMessage("리뷰 조회 성공");
            } else {
                responseBuilder.setSuccess(false)
                        .setMessage("리뷰를 찾을 수 없습니다.");
            }
            
            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            logger.error("Error getting review", e);
            responseObserver.onError(e);
        }
    }
    
    @Override
    public void updateReview(ReviewProto.UpdateReviewRequest request,
                           StreamObserver<ReviewProto.UpdateReviewResponse> responseObserver) {
        try {
            String userId = HeaderInterceptor.USER_ID_CONTEXT_KEY.get();
            if (userId == null || userId.isEmpty()) {
                responseObserver.onError(new StatusRuntimeException(Status.UNAUTHENTICATED.withDescription("User ID is missing")));
                return;
            }
            
            ReviewEntity updatedReview = reviewService.updateReview(
                    request.getRestaurantId(),
                    request.getReviewId(),
                    userId,
                    request.getRating(),
                    request.getContent(),
                    request.getImageUrlsList()
            );
            
            ReviewProto.UpdateReviewResponse response = ReviewProto.UpdateReviewResponse.newBuilder()
                    .setReview(convertToProtoReview(updatedReview))
                    .setSuccess(true)
                    .setMessage("리뷰가 성공적으로 수정되었습니다.")
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            logger.error("Error updating review", e);
            
            ReviewProto.UpdateReviewResponse response = ReviewProto.UpdateReviewResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("리뷰 수정 실패: " + e.getMessage())
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void deleteReview(ReviewProto.DeleteReviewRequest request,
                           StreamObserver<ReviewProto.DeleteReviewResponse> responseObserver) {
        try {
            String userId = HeaderInterceptor.USER_ID_CONTEXT_KEY.get();
            if (userId == null || userId.isEmpty()) {
                responseObserver.onError(new StatusRuntimeException(Status.UNAUTHENTICATED.withDescription("User ID is missing")));
                return;
            }
            
            reviewService.deleteReview(request.getRestaurantId(), request.getReviewId(), userId);
            
            ReviewProto.DeleteReviewResponse response = ReviewProto.DeleteReviewResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("리뷰가 성공적으로 삭제되었습니다.")
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            logger.error("Error deleting review", e);
            
            ReviewProto.DeleteReviewResponse response = ReviewProto.DeleteReviewResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("리뷰 삭제 실패: " + e.getMessage())
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void verifyReceipt(ReviewProto.VerifyReceiptRequest request,
                            StreamObserver<ReviewProto.VerifyReceiptResponse> responseObserver) {
        try {
            OcrResultDto ocrResult = reviewService.verifyReceipt(
                    request.getReceiptImage().toByteArray(),
                    request.getExpectedRestaurantName(),
                    request.getExpectedAddress()
            );
            
            ReviewProto.VerifyReceiptResponse response = ReviewProto.VerifyReceiptResponse.newBuilder()
                    .setOcrResult(convertToProtoOcrResult(ocrResult))
                    .setIsVerified(ocrResult.isValid())
                    .setMessage(ocrResult.isValid() ? "영수증 검증 성공" : "영수증 검증 실패")
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            logger.error("Error verifying receipt", e);
            responseObserver.onError(e);
        }
    }
    
    private ReviewProto.Review convertToProtoReview(ReviewEntity entity) {
        return ReviewProto.Review.newBuilder()
                .setRestaurantId(entity.getRestaurantId())
                .setReviewId(entity.getReviewId())
                .setUserId(entity.getUserId())
                .setRestaurantName(entity.getRestaurantName() != null ? entity.getRestaurantName() : "")
                .setRestaurantAddress(entity.getRestaurantAddress() != null ? entity.getRestaurantAddress() : "")
                .setRating(entity.getRating())
                .setContent(entity.getContent() != null ? entity.getContent() : "")
                .addAllImageUrls(entity.getImageUrls() != null ? entity.getImageUrls() : List.of())
                .setVisitDate(entity.getVisitDate() != null ? entity.getVisitDate() : "")
                .setIsVerified(entity.getIsVerified() != null ? entity.getIsVerified() : false)
                .setCreatedAt(entity.getCreatedAt().toString())
                .setUpdatedAt(entity.getUpdatedAt().toString())
                .build();
    }
    
    private ReviewProto.OcrResult convertToProtoOcrResult(OcrResultDto dto) {
        return ReviewProto.OcrResult.newBuilder()
                .setIsValid(dto.isValid())
                .setRestaurantName(dto.getRestaurantName() != null ? dto.getRestaurantName() : "")
                .setAddress(dto.getAddress() != null ? dto.getAddress() : "")
                .setVisitDate(dto.getVisitDate() != null ? dto.getVisitDate() : "")
                .setTotalAmount(dto.getTotalAmount() != null ? dto.getTotalAmount() : "")
                .setRawText(dto.getRawText() != null ? dto.getRawText() : "")
                .setConfidence(dto.getConfidence())
                .build();
    }

    @Override
    public void getReviewsForRecommendation(ReviewProto.GetReviewsForRecommendationRequest request,
                                          StreamObserver<ReviewProto.GetReviewsForRecommendationResponse> responseObserver) {
        try {
            logger.info("Getting reviews for recommendation - area: {}, category: {}", 
                       request.getArea(), request.getCategory());
            
            List<ReviewEntity> reviews = reviewService.getReviewsForRecommendation(
                    request.getArea(), request.getCategory(), request.getPage(), request.getSize());
            
            ReviewProto.GetReviewsForRecommendationResponse response = 
                    ReviewProto.GetReviewsForRecommendationResponse.newBuilder()
                            .addAllReviews(reviews.stream()
                                    .map(this::convertToProtoReview)
                                    .collect(Collectors.toList()))
                            .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            logger.error("Error getting reviews for recommendation", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void storePlacesForReview(ReviewProto.StorePlacesForReviewRequest request,
                                   StreamObserver<ReviewProto.StorePlacesForReviewResponse> responseObserver) {
        try {
            logger.info("Storing places for review - User ID: {}, Places count: {}", 
                       request.getUserId(), request.getPlacesCount());
            
            // ReviewService에서 미작성 리뷰로 저장
            boolean success = reviewService.savePendingReviews(request.getUserId(), request.getPlacesList());
            
            if (success) {
                ReviewProto.StorePlacesForReviewResponse response = 
                        ReviewProto.StorePlacesForReviewResponse.newBuilder()
                                .setStatus("SUCCESS")
                                .setMessage("Places stored successfully as pending reviews")
                                .setSuccess(true)
                                .build();
                
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            } else {
                ReviewProto.StorePlacesForReviewResponse errorResponse = 
                        ReviewProto.StorePlacesForReviewResponse.newBuilder()
                                .setStatus("ERROR")
                                .setMessage("Failed to store places as pending reviews")
                                .setSuccess(false)
                                .build();
                
                responseObserver.onNext(errorResponse);
                responseObserver.onCompleted();
            }
            
        } catch (Exception e) {
            logger.error("Error storing places for review", e);
            
            ReviewProto.StorePlacesForReviewResponse errorResponse = 
                    ReviewProto.StorePlacesForReviewResponse.newBuilder()
                            .setStatus("ERROR")
                            .setMessage("Failed to store places: " + e.getMessage())
                            .setSuccess(false)
                            .build();
            
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getPlacesForReview(ReviewProto.GetPlacesForReviewRequest request,
                                 StreamObserver<ReviewProto.GetPlacesForReviewResponse> responseObserver) {
        try {
            // 헤더에서 사용자 ID 추출 (HeaderInterceptor 구현 필요)
            String userId = getCurrentUserId();
            
            logger.info("Getting places for review - User ID: {}", userId);
            
            // ReviewService에서 미작성 리뷰 목록 조회
            List<PendingReviewEntity> pendingReviews = reviewService.getPendingReviewsByUserId(userId);
            
            // PendingReviewEntity를 ReviewPlaceInfo로 변환
            List<ReviewProto.ReviewPlaceInfo> places = pendingReviews.stream()
                    .map(pending -> ReviewProto.ReviewPlaceInfo.newBuilder()
                            .setId(pending.getRestaurantId())
                            .setPlaceName(pending.getPlaceName())
                            .setAddressName(pending.getAddressName())
                            .setPlaceUrl(pending.getPlaceUrl() != null ? pending.getPlaceUrl() : "")
                            .setScheduledTime(pending.getScheduledTime())
                            .build())
                    .collect(Collectors.toList());
            
            ReviewProto.GetPlacesForReviewResponse response = 
                    ReviewProto.GetPlacesForReviewResponse.newBuilder()
                            .addAllPlaces(places)
                            .setStatus("SUCCESS")
                            .setMessage("Pending reviews retrieved successfully")
                            .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            logger.error("Error getting places for review", e);
            
            ReviewProto.GetPlacesForReviewResponse errorResponse = 
                    ReviewProto.GetPlacesForReviewResponse.newBuilder()
                            .setStatus("ERROR")
                            .setMessage("Failed to get places: " + e.getMessage())
                            .build();
            
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getReviewSummaryForRecommendation(ReviewProto.GetReviewSummaryRequest request,
                                                StreamObserver<ReviewProto.GetReviewSummaryResponse> responseObserver) {
        try {
            logger.info("Getting review summary for recommendation - Restaurant IDs: {}", 
                       request.getRestaurantIdsList());
            
            // ReviewService에서 리뷰 요약 데이터 조회
            List<ReviewProto.RestaurantReviewSummary> summaries = 
                    reviewService.getReviewSummaryForRecommendation(request.getRestaurantIdsList());
            
            ReviewProto.GetReviewSummaryResponse response = 
                    ReviewProto.GetReviewSummaryResponse.newBuilder()
                            .addAllSummaries(summaries)
                            .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            logger.error("Error getting review summary for recommendation", e);
            responseObserver.onError(e);
        }
    }
    
    private String getCurrentUserId() {
        // HeaderInterceptor에서 설정한 사용자 ID를 가져오는 로직
        String userId = HeaderInterceptor.USER_ID_CONTEXT_KEY.get();
        if (userId == null || userId.isEmpty()) {
            throw new SecurityException("사용자 인증이 필요합니다.");
        }
        return userId;
    }
}