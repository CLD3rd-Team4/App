package mapzip.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import mapzip.gateway.util.JwtUtil;
import mapzip.gateway.util.TokenValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;

class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;
    
    @Mock
    private GatewayFilterChain filterChain;
    
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtUtil, objectMapper);
    }

    @Test
    void shouldAllowLoginRequest() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/auth/kakao/login")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(filterChain.filter(any())).thenReturn(Mono.empty());
        
        // When
        GatewayFilter filter = jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config());
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        verify(filterChain).filter(exchange);
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void shouldReturn401WithTokenInvalidWhenNoJwtCookie() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/auth/profile")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // When
        GatewayFilter filter = jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config());
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertTrue(exchange.getResponse().getHeaders().getFirst("Content-Type").contains("application/json"));
        
        // 응답 바디에 TOKEN_INVALID가 포함되어 있는지 확인
        String responseBody = exchange.getResponse().getBodyAsString().block();
        assertTrue(responseBody.contains("TOKEN_INVALID"));
        
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldReturn401WithTokenInvalidWhenInvalidToken() {
        // Given
        String tokenValue = "invalid-token";
        HttpCookie cookie = new HttpCookie("accessToken", tokenValue);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/auth/profile")
                .cookie(cookie)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(jwtUtil.validateTokenWithResult("invalid-token")).thenReturn(TokenValidationResult.INVALID);
        
        // When
        GatewayFilter filter = jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config());
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        
        // 응답 바디에 TOKEN_INVALID가 포함되어 있는지 확인
        String responseBody = exchange.getResponse().getBodyAsString().block();
        assertTrue(responseBody.contains("TOKEN_INVALID"));
        
        verify(jwtUtil).validateTokenWithResult("invalid-token");
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldReturn401WithTokenExpiredWhenExpiredToken() {
        // Given
        String tokenValue = "expired-token";
        HttpCookie cookie = new HttpCookie("accessToken", tokenValue);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/auth/profile")
                .cookie(cookie)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(jwtUtil.validateTokenWithResult("expired-token")).thenReturn(TokenValidationResult.EXPIRED);
        
        // When
        GatewayFilter filter = jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config());
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        
        // 응답 바디에 TOKEN_EXPIRED가 포함되어 있는지 확인
        String responseBody = exchange.getResponse().getBodyAsString().block();
        assertTrue(responseBody.contains("TOKEN_EXPIRED"));
        
        verify(jwtUtil).validateTokenWithResult("expired-token");
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldPassThroughWhenValidToken() {
        // Given
        String userId = "testuser";
        String validToken = "valid-token";
        HttpCookie cookie = new HttpCookie("accessToken", validToken);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/auth/profile")
                .cookie(cookie)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(jwtUtil.validateTokenWithResult(validToken)).thenReturn(TokenValidationResult.VALID);
        when(jwtUtil.extractUserId(validToken)).thenReturn(userId);
        when(filterChain.filter(any())).thenReturn(Mono.empty());
        
        // When
        GatewayFilter filter = jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config());
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        verify(jwtUtil).validateTokenWithResult(validToken);
        verify(jwtUtil).extractUserId(validToken);
        verify(filterChain).filter(any());
    }
}