package com.mapzip.recommend.config;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mapzip.review.grpc.ReviewServiceGrpc;
import com.mapzip.schedule.grpc.ScheduleServiceGrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

@Configuration
public class GrpcClientConfig {

    @Value("${schedule.grpc.host}")
    private String scheduleHost;

    @Value("${schedule.grpc.port}")
    private int schedulePort;

    @Bean
    public ScheduleServiceGrpc.ScheduleServiceBlockingStub scheduleServiceBlockingStub() {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(scheduleHost, schedulePort)
                .usePlaintext()
                .build();

        return ScheduleServiceGrpc.newBlockingStub(channel);
    }

    // 이미 있는 review 클라이언트도 같이 있어도 됨
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
