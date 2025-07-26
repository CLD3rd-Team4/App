package mapzip.gateway.filter;

import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@Component
public class XssProtectionFilter extends AbstractGatewayFilterFactory<XssProtectionFilter.Config> {

    private final PolicyFactory policy;

    public XssProtectionFilter() {
        super(Config.class);
        this.policy = Sanitizers.FORMATTING.and(Sanitizers.LINKS).and(Sanitizers.BLOCKS);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            
            // GET 요청은 쿼리 파라미터만 필터링
            if ("GET".equals(request.getMethod().name())) {
                URI originalUri = request.getURI();
                String query = originalUri.getQuery();
                
                if (query != null) {
                    String sanitizedQuery = sanitizeXss(query);
                    URI newUri = UriComponentsBuilder.fromUri(originalUri)
                            .replaceQuery(sanitizedQuery)
                            .build()
                            .toUri();
                    
                    ServerHttpRequest filteredRequest = request.mutate()
                            .uri(newUri)
                            .build();
                    
                    return chain.filter(exchange.mutate().request(filteredRequest).build());
                }
                
                return chain.filter(exchange);
            }

            // POST/PUT 요청은 body 필터링
            ServerHttpRequestDecorator decorator = new ServerHttpRequestDecorator(request) {
                @Override
                public Flux<DataBuffer> getBody() {
                    return super.getBody()
                            .collectList()
                            .flatMapMany(dataBuffers -> {
                                String body = dataBuffers.stream()
                                        .map(dataBuffer -> {
                                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                            dataBuffer.read(bytes);
                                            DataBufferUtils.release(dataBuffer);
                                            return new String(bytes, StandardCharsets.UTF_8);
                                        })
                                        .reduce("", String::concat);
                                
                                String sanitizedBody = sanitizeXss(body);
                                DataBuffer buffer = exchange.getResponse().bufferFactory()
                                        .wrap(sanitizedBody.getBytes(StandardCharsets.UTF_8));
                                return Flux.just(buffer);
                            });
                }
            };

            return chain.filter(exchange.mutate().request(decorator).build());
        };
    }

    private String sanitizeXss(String input) {
        if (input == null) return null;
        return policy.sanitize(input);
    }

    public static class Config {
        // 설정이 필요한 경우 여기에 추가
    }
}