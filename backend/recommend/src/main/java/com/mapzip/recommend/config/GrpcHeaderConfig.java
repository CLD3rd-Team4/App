package com.mapzip.recommend.config;
import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import io.grpc.Context;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
@Configuration
public class GrpcHeaderConfig {

    public static final class UserIdContext {
        private UserIdContext() {}
        public static final Context.Key<String> USER_ID = Context.key("x-user-id");
    }

    @Bean
    @Order(100)
    @GrpcGlobalServerInterceptor
    public ServerInterceptor userIdInterceptor() {
        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    ServerCallHandler<ReqT, RespT> next) {

                // x-user-id 헤더 추출
                String userId = headers.get(Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER));
                System.out.println("받아온 x-user-id = " + userId);

                // Context에 userId만 저장
                Context ctx = Context.current().withValue(UserIdContext.USER_ID, userId);

                return Contexts.interceptCall(ctx, call, headers, next);
            }
        };
    }
}