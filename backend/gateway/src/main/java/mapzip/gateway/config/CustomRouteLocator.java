package mapzip.gateway.config;

import mapzip.gateway.filter.JwtAuthenticationFilter;
import mapzip.gateway.filter.XssProtectionFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomRouteLocator {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final XssProtectionFilter xssProtectionFilter;

    public CustomRouteLocator(JwtAuthenticationFilter jwtAuthenticationFilter,
                              XssProtectionFilter xssProtectionFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.xssProtectionFilter = xssProtectionFilter;
    }

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                // 인증 서비스 라우팅
                .route("auth-service", r -> r.path("/auth/**")
                        .filters(f -> f
                                .filter(xssProtectionFilter.apply(new XssProtectionFilter.Config()))
                                .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config())))
                        .uri("http://auth.service-platform:8080"))

                // 추천 서비스 라우팅
                .route("recommend-service", r -> r.path("/recommend/**")
                        .filters(f -> f
                                .filter(xssProtectionFilter.apply(new XssProtectionFilter.Config()))
                                .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config())))
                        .uri("http://recommend.service-recommend:9090"))

                // HTTP (port 8080): 이미지 처리 + HTTP 전용 API
                .route("review-http-post", r -> r.path("/review")
                        .and().method("POST")  // 이미지 업로드 전용
                        .filters(f -> f
                                .filter(xssProtectionFilter.apply(new XssProtectionFilter.Config()))
                                .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config())))
                        .uri("http://review.service-review:8080"))
                
                .route("review-http-verify", r -> r.path("/review/verify-receipt")
                        .and().method("POST")  // OCR 검증 전용
                        .filters(f -> f
                                .filter(xssProtectionFilter.apply(new XssProtectionFilter.Config()))
                                .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config())))
                        .uri("http://review.service-review:8080"))

                .route("review-http-pending", r -> r.path("/review/pending", "/review/pending/**")
                        .and().method("GET", "DELETE")  // 미작성 리뷰 관리
                        .filters(f -> f
                                .filter(xssProtectionFilter.apply(new XssProtectionFilter.Config()))
                                .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config())))
                        .uri("http://review.service-review:8080"))
                
                .route("review-http-health", r -> r.path("/review/health")
                        .and().method("GET")  // 헬스체크
                        .filters(f -> f
                                .filter(xssProtectionFilter.apply(new XssProtectionFilter.Config()))
                                .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config())))
                        .uri("http://review.service-review:8080"))

                // gRPC (port 50051): 일반 조회 API 
                .route("review-grpc", r -> r.path("/review/**")
                        .and().method("GET", "PUT", "DELETE")  // 나머지 API들
                        .filters(f -> f
                                .filter(xssProtectionFilter.apply(new XssProtectionFilter.Config()))
                                .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config())))
                        .uri("http://review.service-review:50051"))

                // 스케줄 서비스 라우팅
                .route("schedule-service", r -> r.path("/schedule/**")
                        .filters(f -> f
                                .filter(xssProtectionFilter.apply(new XssProtectionFilter.Config()))
                                .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config())))
                        .uri("http://schedule.service-schedule:9090"))

                // 데모 서비스 라우팅
                .route("demo-service", r -> r.path("/demo/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .filter(xssProtectionFilter.apply(new XssProtectionFilter.Config()))
                                .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config())))
                        .uri("http://demo.demo:9090"))
                .build();
    }
}
