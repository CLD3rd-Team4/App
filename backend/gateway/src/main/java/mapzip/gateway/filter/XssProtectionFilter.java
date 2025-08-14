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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class XssProtectionFilter extends AbstractGatewayFilterFactory<XssProtectionFilter.Config> {

    private final PolicyFactory policy;
    private final ObjectMapper objectMapper;
    private static final Logger log = LoggerFactory.getLogger(XssProtectionFilter.class);

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
                    log.info("[XSS Filter] GET Query - Original: {}", query);

                    // 안전하게 각 파라미터별로 sanitize
                    String sanitizedQuery = Arrays.stream(query.split("&"))
                            .map(param -> {
                                String[] kv = param.split("=", 2);
                                if (kv.length == 2) {
                                    String key = kv[0];
                                    String value = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                                    String sanitizedValue = sanitizeXss(value);
                                    return URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" +
                                            URLEncoder.encode(sanitizedValue, StandardCharsets.UTF_8);
                                }
                                return param;
                            })
                            .collect(Collectors.joining("&"));

                    log.info("[XSS Filter] GET Query - Sanitized: {}", sanitizedQuery);

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
                                // 1) 모든 DataBuffer 바이트를 합침
                                byte[] bytes = dataBuffers.stream()
                                        .map(DataBuffer::asByteBuffer)
                                        .collect(() -> ByteBuffer.allocate(dataBuffers.stream().mapToInt(DataBuffer::readableByteCount).sum()),
                                                ByteBuffer::put,
                                                ByteBuffer::put)
                                        .array();

                                dataBuffers.forEach(DataBufferUtils::release); // 안전하게 해제

                                String body = new String(bytes, StandardCharsets.UTF_8);
                                String sanitizedBody = sanitizeBody(body, request);
                                byte[] sanitizedBytes = sanitizedBody.getBytes(StandardCharsets.UTF_8);

                                DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(sanitizedBytes);
                                return Flux.just(buffer);
                            });
                }
            };

            ServerHttpRequest filteredRequest = decorator.mutate()
                    .headers(httpHeaders -> httpHeaders.remove("Content-Length"))
                    .build();

            return chain.filter(exchange.mutate().request(filteredRequest).build());
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
            log.debug("[XSS Filter] Parsing JSON body");
            JsonNode rootNode = objectMapper.readTree(jsonBody);
            JsonNode sanitizedNode = sanitizeJsonNode(rootNode);
            return objectMapper.writeValueAsString(sanitizedNode);
        } catch (Exception e) {
            log.warn("[XSS Filter] JSON parsing failed, fallback to text sanitize: {}", e.getMessage());
            return sanitizeXss(jsonBody);
        }
    }

    String sanitizeFormBody(String formBody) {
        try {
            log.debug("[XSS Filter] Parsing form-urlencoded body");
            return Arrays.stream(formBody.split("&"))
                    .map(pair -> {
                        String[] keyValue = pair.split("=", 2);
                        if (keyValue.length == 2) {
                            String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                            String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                            String sanitizedValue = sanitizeXss(value);
                            log.debug("[XSS Filter] Form field '{}': '{}' -> '{}'", key, value, sanitizedValue);
                            return URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" +
                                    URLEncoder.encode(sanitizedValue, StandardCharsets.UTF_8);
                        }
                        return pair;
                    })
                    .collect(Collectors.joining("&"));
        } catch (Exception e) {
            log.warn("[XSS Filter] Form parsing failed, fallback to text sanitize: {}", e.getMessage());
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