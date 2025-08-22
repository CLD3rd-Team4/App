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
                // Metadata에서 "x-user-id" 헤더 값을 추출
                String userId = headers.get(Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER));

                if (userId != null) {
                    log.info("Extracted userId from header: {}", userId);
                    // userId가 존재하면 Context에 저장
                    Context context = Context.current().withValue(USER_ID_CONTEXT_KEY, userId);
                    return Contexts.interceptCall(context, call, headers, next);
                } else {
                    log.warn("x-user-id header not found.");
                    // userId가 없으면 그냥 다음 인터셉터/서비스로 체인 계속
                    return next.startCall(call, headers);
                }
            }
        };
    }
}
