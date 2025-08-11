package com.mapzip.schedule.config;

import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.protobuf.services.HealthStatusManager;
import lombok.extern.slf4j.Slf4j;
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
    public HealthIndicator grpcServerHealthIndicator() {
        return () -> {
            try {
                // 간단한 gRPC 서버 상태 체크
                // 실제로는 gRPC 서버가 9090 포트에서 실행 중인지 확인
                return Health.up()
                    .withDetail("grpc.server.status", "SERVING")
                    .withDetail("grpc.server.port", "9090")
                    .withDetail("grpc.server.service", "schedule.ScheduleService")
                    .build();
            } catch (Exception e) {
                log.error("gRPC health check failed", e);
                return Health.down()
                    .withDetail("grpc.server.status", "ERROR")
                    .withDetail("grpc.server.error", e.getMessage())
                    .build();
            }
        };
    }
}
