package com.mapzip.recommend.service;

import java.util.Arrays;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mapzip.recommend.dto.ReviewStatsDto;
import com.mapzip.review.grpc.ReviewProto.GetReviewSummaryRequest;
import com.mapzip.review.grpc.ReviewProto.GetReviewSummaryResponse;
import com.mapzip.review.grpc.ReviewProto.RestaurantReviewSummary;
import com.mapzip.review.grpc.ReviewServiceGrpc;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReviewClientService {

    private final ReviewServiceGrpc.ReviewServiceBlockingStub reviewStub;

    public ReviewStatsDto getRestaurantStats(String restaurant_id) {
    	GetReviewSummaryRequest request = GetReviewSummaryRequest.newBuilder()
    			.addAllRestaurantIds(Arrays.asList(restaurant_id))
                .build();

    	GetReviewSummaryResponse response = reviewStub.getReviewSummaryForRecommendation(request);
    	
    	//response를 reviewStatsDto로 변경
    	RestaurantReviewSummary summary = response.getSummariesList().get(0);
    	double averageRating = summary.getAverageRating();
    	String joinedReviews = String.join(" | ", summary.getTopReviewsList());
    	
        return new ReviewStatsDto(
                averageRating,
                joinedReviews
        );
        //목데이
//    	return new ReviewStatsDto(4.2, "음식도 맛있고 분위기도 좋아요!");
    }
}
