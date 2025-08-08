package com.mapzip.recommend.service;

import org.springframework.stereotype.Service;

import com.mapzip.recommend.dto.ReviewStatsDto;
import com.mapzip.review.grpc.GetRestaurantStatsRequest;
import com.mapzip.review.grpc.GetRestaurantStatsResponse;
import com.mapzip.review.grpc.ReviewServiceGrpc;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReviewClientService {

    private final ReviewServiceGrpc.ReviewServiceBlockingStub reviewStub;

    public ReviewStatsDto getRestaurantStats(String restaurantId) {
//        GetRestaurantStatsRequest request = GetRestaurantStatsRequest.newBuilder()
//                .setRestaurantId(restaurantId)
//                .build();
//
//        GetRestaurantStatsResponse response = reviewStub.getRestaurantStatsForRecommendation(request);
//
//        return new ReviewStatsDto(
//                response.getAverageRating(),
//                response.getRepresentativeReview()
//        );
    	return new ReviewStatsDto(4.2, "음식도 맛있고 분위기도 좋아요!");
    }
}
