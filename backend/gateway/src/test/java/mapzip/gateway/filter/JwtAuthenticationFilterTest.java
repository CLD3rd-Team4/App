package mapzip.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import mapzip.gateway.util.JwtUtil;
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


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    void shouldReturn401WhenNoJwtCookie() {
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
        
        assert exchange.getResponse().getStatusCode() == HttpStatus.UNAUTHORIZED;
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldReturn401WhenInvalidToken() {
        // Given
        String tokenValue = "invalid-token";
        HttpCookie cookie = ResponseCookie.from("accessToken", tokenValue)
                .path("/")
                .httpOnly(true)
                .build();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/auth/profile")
                .cookie(cookie)  // 여기 HttpCookie 객체 넣기
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(jwtUtil.validateToken("invalid-token")).thenReturn(false);
        
        // When
        GatewayFilter filter = jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config());
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        assert exchange.getResponse().getStatusCode() == HttpStatus.UNAUTHORIZED;
        verify(jwtUtil).validateToken("invalid-token");
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldPassThroughWhenValidToken() {
        // Given
        String userId = "testuser";
        String validToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0dXNlciIsImlhdCI6MTc1MzUxNjA5MywiZXhwIjo4NjQwMDAwMH0.W8hA0tLOLGN6YfDJnyNIrb0iGVKVH909zyP7-o613vI";
        HttpCookie cookie = new HttpCookie("accessToken", validToken);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/auth/profile")
                .cookie(cookie)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(jwtUtil.validateToken(validToken)).thenReturn(true);
        when(jwtUtil.extractUserId(validToken)).thenReturn(userId);
        when(filterChain.filter(any())).thenReturn(Mono.empty());
        
        // When
        GatewayFilter filter = jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config());
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        verify(jwtUtil).validateToken(validToken);
        verify(jwtUtil).extractUserId(validToken);
        verify(filterChain).filter(any());
    }
}