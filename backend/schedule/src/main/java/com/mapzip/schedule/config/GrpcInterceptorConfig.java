package com.mapzip.schedule.config;

import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class GrpcInterceptorConfig {

    // gRPC 컨텍스트에 사용자 ID를 저장하기 위한 Key
    public static final Context.Key<String> USER_ID_CONTEXT_KEY = Context.key("userId");

    @GrpcGlobalServerInterceptor
    public ServerInterceptor userIdInterceptor() {
        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                // 요청된 서비스 메서드 정보 가져오기
                MethodDescriptor<ReqT, RespT> methodDescriptor = call.getMethodDescriptor();

                // gRPC 헬스 체크 요청인 경우, 헤더 검사 없이 바로 통과
                if (methodDescriptor.getServiceName().equals("grpc.health.v1.Health")) {
                    return next.startCall(call, headers);
                }

                // 그 외의 모든 요청(실제 비즈니스 로직)에 대해서만 헤더 검사 수행
                String userId = headers.get(Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER));

                if (userId != null) {
                    log.info("Extracted userId from header: {}", userId);
                    Context context = Context.current().withValue(USER_ID_CONTEXT_KEY, userId);
                    return Contexts.interceptCall(context, call, headers, next);
                } else {
                    log.warn("x-user-id header not found for service: {}", methodDescriptor.getServiceName());
                    // 비즈니스 로직 요청에 헤더가 없으면 에러 반환 (또는 기존처럼 경고만 로깅)
                    // 여기서는 우선 기존 로직대로 경고만 남기고 진행합니다.
                    return next.startCall(call, headers);
                }
            }
        };
    }
}
