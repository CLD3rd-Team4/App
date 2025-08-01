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
                        .uri("http://auth.service-platform:50051"))

                // 추천 서비스 라우팅
                .route("recommend-service", r -> r.path("/recommend/**")
                        .filters(f -> f
                                .filter(xssProtectionFilter.apply(new XssProtectionFilter.Config()))
                                .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config())))
                        .uri("http://recommend.service-recommend:50051"))

                // 리뷰 서비스 라우팅 (이미지 포함 - HTTP)
                .route("review-http", r -> r.path("/review", "/review/verify-receipt")
                        .filters(f -> f
                                .filter(xssProtectionFilter.apply(new XssProtectionFilter.Config()))
                                .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config())))
                        .uri("http://review.service-review:8080"))

                // 리뷰 서비스 라우팅 (이미지 없음 - gRPC)  
                .route("review-grpc", r -> r.path("/review/**")
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
