package mapzip.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mapzip.gateway.util.JwtUtil;
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

            // 로그인 요청, 액세스 토큰 쿠키 재발급 요청은 JWT 검증 제외
            if (path.startsWith("/auth/kakao/login")|| path.startsWith("/auth/token/refresh")) {
                log.info("JWT Filter - Skipping auth for login path: {}", path);
                return chain.filter(exchange);
            }

            String token = extractTokenFromCookie(request);
            log.debug("JWT Filter - Token extracted: {}", token != null ? "present" : "null");

            if (token == null) {
                log.warn("JWT Filter - No token found in cookies");
                ServerHttpResponse response = exchange.getResponse();
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return response.setComplete();
            }

            if (!jwtUtil.validateToken(token)) {
                log.warn("JWT Filter - Invalid token");
                ServerHttpResponse response = exchange.getResponse();
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return response.setComplete();
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

    public static class Config {
        // 설정이 필요한 경우 여기에 추가
    }
}
