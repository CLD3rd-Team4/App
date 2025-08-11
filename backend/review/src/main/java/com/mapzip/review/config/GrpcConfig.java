package com.mapzip.review.config;

import com.mapzip.review.grpc.GrpcHeaderInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcConfig {

    /**
     * gRPC 서버 전역 인터셉터 등록
     * Gateway에서 전달된 HTTP 헤더를 gRPC Context로 전달
     */
    @Bean
    @GrpcGlobalServerInterceptor
    public GrpcHeaderInterceptor grpcHeaderInterceptor() {
        return new GrpcHeaderInterceptor();
    }
}