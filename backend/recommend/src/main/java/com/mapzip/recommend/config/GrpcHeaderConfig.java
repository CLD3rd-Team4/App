package com.mapzip.recommend.config;
import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import io.grpc.Context;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
@Configuration
public class GrpcHeaderConfig {

    public static final class UserIdContext {
        private UserIdContext() {}
        public static final Context.Key<String> USER_ID = Context.key("x-user-id");
    }

    @Bean
    @Order(100)
    @GrpcGlobalServerInterceptor
    public ServerInterceptor userIdInterceptor() {
      return new ServerInterceptor() {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

          String userId = headers.get(Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER));
          String method = call.getMethodDescriptor().getFullMethodName();
          Object remote = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
          String reqId = java.util.UUID.randomUUID().toString().substring(0,8);

          System.out.printf("[IN %s] method=%s remote=%s x-user-id=%s%n", reqId, method, remote, userId);

          Context ctx = Context.current().withValue(UserIdContext.USER_ID, userId);
          return Contexts.interceptCall(ctx, call, headers, next);
        }
      };
    }

}