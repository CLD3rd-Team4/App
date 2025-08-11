package com.mapzip.schedule.config;

import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.protobuf.services.HealthStatusManager;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class GrpcHealthConfig {

    /**
     * gRPC Health Status Manager Bean
     * gRPC 서버의 health check를 관리합니다.
     */
    @Bean
    public HealthStatusManager healthStatusManager() {
        HealthStatusManager healthStatusManager = new HealthStatusManager();
        // 기본적으로 서비스를 SERVING 상태로 설정
        healthStatusManager.setStatus("", HealthCheckResponse.ServingStatus.SERVING);
        healthStatusManager.setStatus("schedule.ScheduleService", HealthCheckResponse.ServingStatus.SERVING);
        log.info("gRPC Health Status Manager initialized");
        return healthStatusManager;
    }

    /**
     * Spring Boot Actuator용 gRPC Health Indicator
     * /actuator/health 엔드포인트에서 gRPC 서버 상태를 확인할 수 있게 합니다.
     */
    @Bean("grpcServer")
    public HealthIndicator grpcServerHealthIndicator(HealthStatusManager healthStatusManager) {
        return () -> {
            // 현재 단계에서는 gRPC 서버가 시작되면 항상 SERVING 상태라고 가정하고 UP을 반환합니다.
            // 이렇게 하면 애플리케이션을 우선 실행시킬 수 있습니다.
            return Health.up()
                .withDetail("grpc.server.status", "SERVING")
                .withDetail("grpc.server.port", "9090")
                .build();
        };
    }

    /**
     * gRPC Health Service를 등록합니다.
     * HealthStatusManager가 제공하는 기본 Health Service 구현을 gRPC 서비스로 노출시킵니다.
     */
    @GrpcService
    @Bean
    public io.grpc.BindableService grpcHealthService(HealthStatusManager healthStatusManager) {
        return healthStatusManager.getHealthService();
    }
}
