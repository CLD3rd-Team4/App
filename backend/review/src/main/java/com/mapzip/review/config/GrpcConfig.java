package com.mapzip.review.config;

import com.mapzip.review.grpc.HeaderInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcConfig {

    @GrpcGlobalServerInterceptor
    public HeaderInterceptor headerInterceptor() {
        return new HeaderInterceptor();
    }
}
