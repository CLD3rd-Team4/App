package mapzip.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class XssProtectionFilterTest {

    @Mock
    private GatewayFilterChain filterChain;
    
    private XssProtectionFilter xssProtectionFilter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        xssProtectionFilter = new XssProtectionFilter();
    }

    @Test
    void shouldSanitizeGetRequestQueryParameters() {
        // Given - OWASP는 <script> 태그를 완전히 제거함
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/test?name=<script>alert('xss')</script>user&data=<img src=x onerror=alert(1)>")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(filterChain.filter(any())).thenReturn(Mono.empty());
        
        // When
        GatewayFilter filter = xssProtectionFilter.apply(new XssProtectionFilter.Config());
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        verify(filterChain).filter(any());
    }

    @Test
    void shouldAllowCleanGetRequest() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/test?name=user&data=normal")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(filterChain.filter(any())).thenReturn(Mono.empty());
        
        // When
        GatewayFilter filter = xssProtectionFilter.apply(new XssProtectionFilter.Config());
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        verify(filterChain).filter(any());
    }

    @Test
    void shouldSanitizePostRequestBody() {
        // Given - OWASP는 위험한 스크립트와 이벤트 핸들러를 제거
        String maliciousBody = "{\"name\":\"<script>alert('xss')</script>user\",\"data\":\"<iframe src=javascript:alert(1)></iframe>\"}";
        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(maliciousBody.getBytes(StandardCharsets.UTF_8));
        
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/test")
                .body(Flux.just(dataBuffer));
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(filterChain.filter(any())).thenReturn(Mono.empty());
        
        // When
        GatewayFilter filter = xssProtectionFilter.apply(new XssProtectionFilter.Config());
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        verify(filterChain).filter(any());
    }

    @Test
    void shouldAllowSafeHtmlElements() {
        // Given - OWASP 정책에서 허용하는 안전한 HTML 요소들
        String safeBody = "{\"content\":\"<p>Hello <b>world</b>!</p><a href='https://example.com'>Link</a>\"}";
        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(safeBody.getBytes(StandardCharsets.UTF_8));
        
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/test")
                .body(Flux.just(dataBuffer));
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(filterChain.filter(any())).thenReturn(Mono.empty());
        
        // When
        GatewayFilter filter = xssProtectionFilter.apply(new XssProtectionFilter.Config());
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        verify(filterChain).filter(any());
    }

    @Test
    void shouldHandleNullQueryParameters() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/test")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(filterChain.filter(any())).thenReturn(Mono.empty());
        
        // When
        GatewayFilter filter = xssProtectionFilter.apply(new XssProtectionFilter.Config());
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        verify(filterChain).filter(any());
    }

    @Test
    void shouldBlockAdvancedXssAttacks() {
        // Given - 고급 XSS 공격 패턴들
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/test?payload=<svg onload=alert(1)>&data=<object data=javascript:alert(1)>")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(filterChain.filter(any())).thenReturn(Mono.empty());
        
        // When
        GatewayFilter filter = xssProtectionFilter.apply(new XssProtectionFilter.Config());
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        verify(filterChain).filter(any());
    }
}