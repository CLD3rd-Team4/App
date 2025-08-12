package mapzip.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class XssProtectionFilter extends AbstractGatewayFilterFactory<XssProtectionFilter.Config> {

    private final PolicyFactory policy;
    private final ObjectMapper objectMapper;

    public XssProtectionFilter() {
        super(Config.class);
        this.policy = Sanitizers.FORMATTING.and(Sanitizers.LINKS).and(Sanitizers.BLOCKS);
        this.objectMapper = new ObjectMapper();
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
                                
                                String sanitizedBody = sanitizeBody(body, request);
                                DataBuffer buffer = exchange.getResponse().bufferFactory()
                                        .wrap(sanitizedBody.getBytes(StandardCharsets.UTF_8));
                                return Flux.just(buffer);
                            });
                }
            };

            return chain.filter(exchange.mutate().request(decorator).build());
        };
    }

    String sanitizeXss(String input) {
        if (input == null) return null;
        return policy.sanitize(input);
    }

    String sanitizeBody(String body, ServerHttpRequest request) {
        if (body == null || body.isEmpty()) return body;
        
        MediaType contentType = request.getHeaders().getContentType();
        if (MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            return sanitizeJsonBody(body);
        } else if (MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(contentType)) {
            return sanitizeFormBody(body);
        }
        
        return sanitizeXss(body);
    }

    String sanitizeJsonBody(String jsonBody) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonBody);
            JsonNode sanitizedNode = sanitizeJsonNode(rootNode);
            return objectMapper.writeValueAsString(sanitizedNode);
        } catch (Exception e) {
            return sanitizeXss(jsonBody);
        }
    }

    String sanitizeFormBody(String formBody) {
        try {
            return Arrays.stream(formBody.split("&"))
                    .map(pair -> {
                        String[] keyValue = pair.split("=", 2);
                        if (keyValue.length == 2) {
                            String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                            String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                            String sanitizedValue = sanitizeXss(value);
                            return URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" + 
                                   URLEncoder.encode(sanitizedValue, StandardCharsets.UTF_8);
                        }
                        return pair;
                    })
                    .collect(Collectors.joining("&"));
        } catch (Exception e) {
            return sanitizeXss(formBody);
        }
    }

    private JsonNode sanitizeJsonNode(JsonNode node) {
        if (node.isTextual()) {
            return objectMapper.getNodeFactory().textNode(sanitizeXss(node.asText()));
        } else if (node.isObject()) {
            ObjectNode objectNode = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry -> 
                objectNode.set(entry.getKey(), sanitizeJsonNode(entry.getValue()))
            );
            return objectNode;
        } else if (node.isArray()) {
            var arrayNode = objectMapper.createArrayNode();
            node.forEach(item -> arrayNode.add(sanitizeJsonNode(item)));
            return arrayNode;
        }
        return node;
    }

    public static class Config {
        // 설정이 필요한 경우 여기에 추가
    }
}