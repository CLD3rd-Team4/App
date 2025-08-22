package mapzip.gateway.config;

import mapzip.gateway.filter.JwtAuthenticationFilter;
import mapzip.gateway.filter.XssProtectionFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@SpringBootTest
@TestPropertySource(properties = {
    "jwt.secret=test-secret-key-for-testing-purposes-only",
    "spring.cloud.config.enabled=false"
})
class GatewayMockTest {

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    
    @Autowired
    private XssProtectionFilter xssProtectionFilter;

    @TestConfiguration
    static class TestConfig {
        
        @Bean
        @Primary
        public JwtAuthenticationFilter jwtAuthenticationFilter() {
            return mock(JwtAuthenticationFilter.class);
        }
        
        @Bean
        @Primary
        public XssProtectionFilter xssProtectionFilter() {
            return mock(XssProtectionFilter.class);
        }
    }


    @Test
    void shouldConfigureAuthServiceRoute() {
        // Mock 필터 설정 - 실제 필터 로직 없이 통과만 하도록 설정
        doReturn(mockGatewayFilter()).when(jwtAuthenticationFilter).apply(any(JwtAuthenticationFilter.Config.class));
        doReturn(mockGatewayFilter()).when(xssProtectionFilter).apply(any(XssProtectionFilter.Config.class));

        // 설정된 모든 라우트 조회
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        
        // auth-service 라우트 찾기
        Route authRoute = routes.stream()
            .filter(route -> "auth-service".equals(route.getId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("auth-service route not found"));
            
        // URI 검증: 인증 서비스의 올바른 주소로 라우팅되는지 확인
        assertThat(authRoute.getUri().toString())
            .isEqualTo("http://auth.service-platform:50051");
        // 경로 패턴 검증: /auth/** 패턴이 설정되어 있는지 확인
        assertThat(authRoute.getPredicate().toString())
            .contains("/auth/**");
    }


    @Test
    void shouldConfigureRecommendServiceRoute() {
        doReturn(mockGatewayFilter()).when(jwtAuthenticationFilter).apply(any(JwtAuthenticationFilter.Config.class));
        doReturn(mockGatewayFilter()).when(xssProtectionFilter).apply(any(XssProtectionFilter.Config.class));

        List<Route> routes = routeLocator.getRoutes().collectList().block();
        

        Route recommendRoute = routes.stream()
            .filter(route -> "recommend-service".equals(route.getId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("recommend-service route not found"));
            

        assertThat(recommendRoute.getUri().toString())
            .isEqualTo("http://recommend.service-recommend:50051");

        assertThat(recommendRoute.getPredicate().toString())
            .contains("/recommend/**");
    }


    @Test
    void shouldConfigureAllRoutes() {
        doReturn(mockGatewayFilter()).when(jwtAuthenticationFilter).apply(any(JwtAuthenticationFilter.Config.class));
        doReturn(mockGatewayFilter()).when(xssProtectionFilter).apply(any(XssProtectionFilter.Config.class));

        List<Route> routes = routeLocator.getRoutes().collectList().block();
        
        // 라우트 개수 검증: 정확히 4개의 서비스 라우트가 설정되어야 함
        assertThat(routes).hasSize(4);
        
        // 모든 라우트 ID 추출
        List<String> routeIds = routes.stream()
            .map(Route::getId)
            .toList();
            
        // 라우트 ID 검증: 4개 마이크로서비스 라우트가 모두 존재하는지 확인
        assertThat(routeIds).containsExactlyInAnyOrder(
            "auth-service",      // 인증 서비스
            "recommend-service", // 추천 서비스
            "review-service",    // 리뷰 서비스
            "schedule-service"   // 스케줄 서비스
        );
    }

 // 테스트용 더미 필터 생성
    private GatewayFilter mockGatewayFilter() {
        return (exchange, chain) -> chain.filter(exchange);
    }
}