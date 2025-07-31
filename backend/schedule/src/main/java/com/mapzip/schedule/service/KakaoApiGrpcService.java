package com.mapzip.schedule.service;

import com.mapzip.schedule.grpc.KakaoApiServiceGrpc;
import com.mapzip.schedule.grpc.KakaoSearchRequest;
import com.mapzip.schedule.grpc.KakaoSearchResponse;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class KakaoApiGrpcService extends KakaoApiServiceGrpc.KakaoApiServiceImplBase {

    private final KakaoApiService kakaoApiService; // 실제 로직을 위임할 서비스

    @Override
    public void searchRestaurants(KakaoSearchRequest request, StreamObserver<KakaoSearchResponse> responseObserver) {
        try {
            log.info("gRPC request received for Kakao API search. Slot ID: {}", request.getSlotId());

            // [수정] 실제 서비스 로직 호출
            kakaoApiService.processAndSaveSingleRestaurantSuggestion(
                    request.getSlotId(),
                    request.getLat(),
                    request.getLon(),
                    request.getRadius()
            );

            KakaoSearchResponse response = KakaoSearchResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Request for slot " + request.getSlotId() + " processed successfully.")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in KakaoApiGrpcService for slot {}", request.getSlotId(), e);
            responseObserver.onError(
                io.grpc.Status.INTERNAL
                    .withDescription("Error processing Kakao search request: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }
}
