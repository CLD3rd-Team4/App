package com.mapzip.recommend.config;

import com.google.protobuf.util.JsonFormat;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import com.mapzip.recommend.grpc.GetRecommendationResultsRequest;
import com.mapzip.recommend.grpc.GetRecommendationResultsResponse;
import com.mapzip.recommend.grpc.RecommendServiceGrpc;
import com.mapzip.recommend.grpc.SelectedPlaceRequest;

import static org.springframework.web.servlet.function.RequestPredicates.GET;
import static org.springframework.web.servlet.function.RequestPredicates.POST;
import static org.springframework.web.servlet.function.RouterFunctions.route;
import jakarta.annotation.PreDestroy;



@Configuration
@Profile("local") // 로컬에서만 활성화 (운영은 게이트웨이/Envoy가 처리)
@RequiredArgsConstructor
public class CorsConfig implements WebMvcConfigurer {

    // === gRPC 채널/스텁 Bean ===
    @Value("${grpc.client.recommend.host:localhost}")
    private String grpcHost;

    @Value("${grpc.client.recommend.port:9090}")
    private int grpcPort;

    private ManagedChannel channel;

    @Bean
    public RecommendServiceGrpc.RecommendServiceBlockingStub recommendStub() {
        // 로컬에선 평문이면 충분(usePlaintext). TLS 쓰면 .useTransportSecurity() 로 교체
        this.channel = NettyChannelBuilder.forAddress(grpcHost, grpcPort)
                .usePlaintext()
                .build();
        return RecommendServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) channel.shutdownNow();
    }

    // === HTTP → gRPC 라우트 (컨트롤러 없이) ===
    @Bean
    public RouterFunction<ServerResponse> httpToGrpcRoutes(
        RecommendServiceGrpc.RecommendServiceBlockingStub recommendStub
    ) {
        return
            // 기존 GET /recommend/result 라우트 ...
            route(GET("/recommend/result"), req -> {
                var userId = req.param("userId").orElse(null);
                var scheduleId = req.param("scheduleId").orElse(null);
                if (userId == null || scheduleId == null) {
                    return ServerResponse.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"BAD_REQUEST\",\"message\":\"userId, scheduleId 필요\"}");
                }

                var grpcReq = GetRecommendationResultsRequest.newBuilder()
                    .setUserId(userId)
                    .setScheduleId(scheduleId)
                    .build();

                var grpcRes = recommendStub.getRecommendationResults(grpcReq);
                var json = JsonFormat.printer()
                    .includingDefaultValueFields()
                    .preservingProtoFieldNames()
                    .print(grpcRes);

                return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(json);
            })

            // 🔥 신규: 선택 제출 (POST /recommend/submit)
            .and(route(POST("/recommend/submit"), req -> {
                try {
                    // JSON → Proto
                    String body = req.body(String.class);

                    var builder = SelectedPlaceRequest.newBuilder();
                    JsonFormat.parser()
                        .ignoringUnknownFields() // 여분 필드 무시
                        .merge(body, builder);

                    var grpcRes = recommendStub.submitSelectedPlace(builder.build());

                    // Proto → JSON
                    String json = JsonFormat.printer()
                        .includingDefaultValueFields()
                        .preservingProtoFieldNames()
                        .print(grpcRes);

                    return ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(json);
                } catch (Exception e) {
                    // 파싱/통신 오류 처리
                    return ServerResponse.status(500)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"ERROR\",\"message\":\"submit 실패: " + e.getMessage() + "\"}");
                }
            }));
    
    }

    // === CORS (로컬 out 서버 허용) ===
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:4173", "http://127.0.0.1:4173")
                .allowedMethods("GET","POST","PUT","PATCH","DELETE","OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}