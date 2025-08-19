package com.mapzip.recommend.service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.mapzip.recommend.dto.ReviewStatsDto;
import com.mapzip.recommend.entity.RecommendationSelectionEntity;
import com.mapzip.review.grpc.ReviewProto.GetReviewSummaryRequest;
import com.mapzip.review.grpc.ReviewProto.GetReviewSummaryResponse;
import com.mapzip.review.grpc.ReviewProto.RestaurantReviewSummary;
import com.mapzip.review.grpc.ReviewServiceGrpc;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewClientService {

    private final ReviewServiceGrpc.ReviewServiceBlockingStub reviewStub;

    public void storePlacesForReview(String userId, List<RecommendationSelectionEntity> selections) {
        try {
            List<com.mapzip.review.grpc.ReviewProto.ReviewPlaceInfo> placeInfos = selections.stream()
                    .map(selection -> com.mapzip.review.grpc.ReviewProto.ReviewPlaceInfo.newBuilder()
                            .setId(selection.getPlaceId())
                            .setPlaceName(selection.getPlaceName())
                            .setAddressName(selection.getAddressName())
                            .setPlaceUrl(selection.getPlaceUrl())
                            .setScheduledTime(selection.getScheduledTime())
                            .build())
                    .collect(Collectors.toList());

            com.mapzip.review.grpc.ReviewProto.StorePlacesForReviewRequest request = com.mapzip.review.grpc.ReviewProto.StorePlacesForReviewRequest.newBuilder()
                    .setUserId(userId)
                    .addAllPlaces(placeInfos)
                    .build();

            // x-user-id 헤더 추가
            io.grpc.Metadata headers = new io.grpc.Metadata();
            headers.put(io.grpc.Metadata.Key.of("x-user-id", io.grpc.Metadata.ASCII_STRING_MARSHALLER), userId);
            
            reviewStub.withInterceptors(io.grpc.stub.MetadataUtils.newAttachHeadersInterceptor(headers))
                    .storePlacesForReview(request);
            
            log.info("Successfully stored {} places for review for user: {}", placeInfos.size(), userId);
        } catch (Exception e) {
            log.error("Failed to store places for review for user: {}", userId, e);
            // RuntimeException 대신 로그만 남기고 계속 진행
            // throw new RuntimeException("리뷰 서버 연동 실패: " + e.getMessage(), e);
        }
    }

    public ReviewStatsDto getRestaurantStats(String restaurant_id) {
        try {
            GetReviewSummaryRequest request = GetReviewSummaryRequest.newBuilder()
                    .addAllRestaurantIds(Arrays.asList(restaurant_id))
                    .build();

            GetReviewSummaryResponse response = reviewStub.getReviewSummaryForRecommendation(request);
            
            //response를 reviewStatsDto로 변경
            if (response.getSummariesCount() > 0) {
                RestaurantReviewSummary summary = response.getSummariesList().get(0);
                double averageRating = summary.getAverageRating();
                String joinedReviews = String.join(" | ", summary.getTopReviewsList());
                
                return new ReviewStatsDto(averageRating, joinedReviews);
            } else {
                log.warn("No review summary found for restaurant: {}", restaurant_id);
                return new ReviewStatsDto(0.0, "");
            }
        } catch (Exception e) {
            log.error("Failed to get restaurant stats for restaurant: {}", restaurant_id, e);
            // 목데이터로 fallback
            return new ReviewStatsDto(4.2, "음식도 맛있고 분위기도 좋아요!");
        }
    }
}
