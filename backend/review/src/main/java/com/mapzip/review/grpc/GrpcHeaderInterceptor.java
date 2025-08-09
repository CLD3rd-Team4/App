package com.mapzip.review.grpc;

import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Gateway에서 전달된 HTTP 헤더를 gRPC Context로 전달하는 인터셉터
 * Gateway의 JwtAuthenticationFilter에서 검증 후 주입된 x-user-id 헤더를 추출
 */
@Component
public class GrpcHeaderInterceptor implements ServerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(GrpcHeaderInterceptor.class);
    
    // Context Key for storing user ID
    public static final Context.Key<String> USER_ID_CONTEXT_KEY = Context.key("x-user-id");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        // Gateway에서 HTTP 헤더로 전달된 x-user-id 추출
        String userId = headers.get(Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER));
        
        if (userId == null || userId.isEmpty()) {
            logger.warn("No x-user-id header found in gRPC request. Method: {}", 
                      call.getMethodDescriptor().getFullMethodName());
            // 개발환경에서는 경고만 출력, 운영환경에서는 인증 오류 발생
            if (isProductionEnvironment()) {
                call.close(Status.UNAUTHENTICATED.withDescription("Missing x-user-id header"), headers);
                return new ServerCall.Listener<ReqT>() {};
            }
            // 개발환경에서는 기본값 사용
            userId = "anonymous-dev-user-" + System.currentTimeMillis();
        } else {
            logger.debug("Extracted User-Id from header: {} for method: {}", 
                        userId, call.getMethodDescriptor().getFullMethodName());
        }

        // Context에 사용자 ID 저장
        Context context = Context.current().withValue(USER_ID_CONTEXT_KEY, userId);
        return Contexts.interceptCall(context, call, headers, next);
    }
    
    /**
     * 운영환경 여부 확인
     * Spring Profile이 'prod'인 경우 운영환경으로 판단
     */
    private boolean isProductionEnvironment() {
        String activeProfile = System.getProperty("spring.profiles.active", "dev");
        return "prod".equals(activeProfile);
    }
}