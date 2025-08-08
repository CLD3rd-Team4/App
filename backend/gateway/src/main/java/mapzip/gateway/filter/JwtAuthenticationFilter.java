package mapzip.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mapzip.gateway.util.JwtUtil;
import mapzip.gateway.util.TokenValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getPath().value();

            log.info("JWT Filter - Request: {} {}", request.getMethod(), path);

            // 로그인 요청, 액세스 토큰 쿠키 재발급, 로그아웃 요청은 JWT 검증 제외
            if (path.startsWith("/auth/kakao/callback")|| path.startsWith("/auth/token/")) {
                log.info("JWT Filter - Skipping auth for login path: {}", path);
                return chain.filter(exchange);
            }

            String token = extractTokenFromCookie(request);
            log.debug("JWT Filter - Token extracted: {}", token != null ? "present" : "null");

            if (token == null) {
                log.warn("JWT Filter - No token found in cookies");
                return createErrorResponse(exchange.getResponse(), "TOKEN_INVALID");
            }

            TokenValidationResult validationResult = jwtUtil.validateTokenWithResult(token);
            if (validationResult != TokenValidationResult.VALID) {
                String errorMessage = validationResult == TokenValidationResult.EXPIRED ? "TOKEN_EXPIRED" : "TOKEN_INVALID";
                log.warn("JWT Filter - Token validation failed: {}", errorMessage);
                return createErrorResponse(exchange.getResponse(), errorMessage);
            }

            // JWT에서 사용자 ID 추출하여 http 헤더에 추가
            String userId = jwtUtil.extractUserId(token);
            log.info("JWT Filter - User authenticated: {}", userId);

            ServerHttpRequest modifiedRequest = request.mutate()
                    .header("x-user-id", userId) //gRPC 메타데이터 헤더는 모두 소문자
                    .build();

            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        };
    }

    private String extractTokenFromCookie(ServerHttpRequest request) {
        return request.getCookies().getFirst("accessToken") != null ?
                Objects.requireNonNull(request.getCookies().getFirst("accessToken")).getValue() : null;
    }
    
    private Mono<Void> createErrorResponse(ServerHttpResponse response, String errorMessage) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");
        
        try {
            Map<String, String> errorBody = new HashMap<>();
            errorBody.put("error", errorMessage);
            String jsonResponse = objectMapper.writeValueAsString(errorBody);
            
            org.springframework.core.io.buffer.DataBuffer buffer = response.bufferFactory().wrap(jsonResponse.getBytes());
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Error creating JSON response", e);
            return response.setComplete();
        }
    }

    public static class Config {
        // 설정이 필요한 경우 여기에 추가
    }
}
