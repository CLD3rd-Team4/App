package com.mapzip.recommend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mapzip.review.grpc.ReviewServiceGrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

@Configuration
public class GrpcClientConfig {

    @Value("${review.grpc.host}")
    private String reviewHost;

    @Value("${review.grpc.port}")
    private int reviewPort;

    @Bean
    public ReviewServiceGrpc.ReviewServiceBlockingStub reviewServiceBlockingStub() {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(reviewHost, reviewPort)
                .usePlaintext()
                .build();

        return ReviewServiceGrpc.newBlockingStub(channel);
    }
}
