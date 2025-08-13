package com.mapzip.recommend.grpc;

import com.mapzip.recommend.dto.MultiSlotRecommendRequestDto;
import com.mapzip.recommend.dto.SlotInfoDto;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.stereotype.Component;

@Component
public class RecommendGrpcClient {

    private final RecommendServiceGrpc.RecommendServiceBlockingStub stub;

    public RecommendGrpcClient() {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("localhost", 9090)  
                .usePlaintext() 
                .build();
        stub = RecommendServiceGrpc.newBlockingStub(channel);
    }

    
}
