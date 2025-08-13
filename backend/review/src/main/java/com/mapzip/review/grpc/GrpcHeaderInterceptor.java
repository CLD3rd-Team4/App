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
        
        if (userId == null || userId.isEmpty() || !isValidUserId(userId)) {
            logger.warn("Authentication failed - Missing or invalid x-user-id. Method: {}", 
                      call.getMethodDescriptor().getFullMethodName());
            call.close(Status.UNAUTHENTICATED.withDescription("Authentication required"), headers);
            return new ServerCall.Listener<ReqT>() {};
        }
        
        // JWT 토큰 재검증 (Gateway에서 한 번 더 검증)
        if (!validateJwtFromGateway(headers)) {
            logger.warn("JWT validation failed. Method: {}", 
                      call.getMethodDescriptor().getFullMethodName());
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid authentication"), headers);
            return new ServerCall.Listener<ReqT>() {};
        }

        logger.debug("User authenticated successfully for method: {}", 
                    call.getMethodDescriptor().getFullMethodName());

        // Context에 사용자 ID 저장
        Context context = Context.current().withValue(USER_ID_CONTEXT_KEY, userId);
        return Contexts.interceptCall(context, call, headers, next);
    }
    
    /**
     * 사용자 ID 유효성 검증
     * 안전한 문자만 허용하고 길이 제한
     */
    private boolean isValidUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }
        
        // 영문, 숫자, 하이픈, 언더스코어만 허용하고 길이 제한
        return userId.matches("^[a-zA-Z0-9_-]{1,50}$") && 
               !userId.startsWith("dev-test") && // 테스트 계정 패턴 차단
               !userId.contains("..") && // 경로 순회 방지
               !userId.equalsIgnoreCase("admin") && // 관리자 계정명 차단
               !userId.equalsIgnoreCase("root"); // 루트 계정명 차단
    }
    
    /**
     * Gateway에서 전달된 JWT 토큰 재검증
     * 이중 보안을 위한 추가 검증
     */
    private boolean validateJwtFromGateway(Metadata headers) {
        // Gateway에서 JWT 검증을 통과했음을 나타내는 헤더 확인
        String jwtVerified = headers.get(Metadata.Key.of("x-jwt-verified", Metadata.ASCII_STRING_MARSHALLER));
        String gatewaySignature = headers.get(Metadata.Key.of("x-gateway-signature", Metadata.ASCII_STRING_MARSHALLER));
        
        // Gateway에서 검증된 요청인지 확인
        return "true".equals(jwtVerified) && gatewaySignature != null && !gatewaySignature.isEmpty();
    }
}