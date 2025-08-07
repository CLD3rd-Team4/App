package com.mapzip.review.grpc;

import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HeaderInterceptor implements ServerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(HeaderInterceptor.class);
    public static final Context.Key<String> USER_ID_CONTEXT_KEY = Context.key("x-user-id");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String userId = headers.get(Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER));
        
        if (userId == null || userId.isEmpty()) {
            logger.warn("No x-user-id header found in gRPC request. Method: {}", call.getMethodDescriptor().getFullMethodName());
        } else {
            logger.debug("Extracted User-Id from header: {} for method: {}", userId, call.getMethodDescriptor().getFullMethodName());
        }

        Context context = Context.current().withValue(USER_ID_CONTEXT_KEY, userId);
        return Contexts.interceptCall(context, call, headers, next);
    }
}
