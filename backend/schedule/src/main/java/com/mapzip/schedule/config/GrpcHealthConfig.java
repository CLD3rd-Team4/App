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
            try {
                // gRPC Health Status Manager에서 상태 확인
                HealthCheckResponse.ServingStatus status = healthStatusManager.getHealthService()
                    .check(io.grpc.health.v1.HealthCheckRequest.newBuilder().build())
                    .getStatus();
                
                if (status == HealthCheckResponse.ServingStatus.SERVING) {
                    return Health.up()
                        .withDetail("grpc.server.status", "SERVING")
                        .withDetail("grpc.server.port", "9090")
                        .build();
                } else {
                    return Health.down()
                        .withDetail("grpc.server.status", status.toString())
                        .withDetail("grpc.server.port", "9090")
                        .build();
                }
            } catch (Exception e) {
                log.error("gRPC health check failed", e);
                return Health.down()
                    .withDetail("grpc.server.status", "ERROR")
                    .withDetail("grpc.server.error", e.getMessage())
                    .build();
            }
        };
    }

    /**
     * gRPC Health Service를 등록합니다.
     * 이 서비스는 gRPC 클라이언트가 서버 상태를 확인할 때 사용됩니다.
     */
    @GrpcService
    public static class GrpcHealthService extends io.grpc.protobuf.services.HealthStatusManager.HealthServiceImpl {
        public GrpcHealthService(HealthStatusManager healthStatusManager) {
            super(healthStatusManager);
        }
    }
}
