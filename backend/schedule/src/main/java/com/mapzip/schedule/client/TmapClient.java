package com.mapzip.schedule.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapzip.schedule.dto.TmapRouteRequest;
import com.mapzip.schedule.dto.TmapRouteResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.FileWriter;
import java.io.IOException;

@Slf4j
@Component
public class TmapClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${external.api.tmap.key}")
    private String tmapApiKey;

    public TmapClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper,
                      @Value("${external.api.tmap.url}") String tmapApiUrl) {
        this.webClient = webClientBuilder.baseUrl(tmapApiUrl).build();
        this.objectMapper = objectMapper;
    }

    public Mono<TmapRouteResponse> getRoutePrediction(TmapRouteRequest requestBody) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(requestBody);
            log.info("TMap API Request Payload: {}", jsonPayload);
            try (FileWriter file = new FileWriter("tmap_request_payload.json")) {
                file.write(jsonPayload);
            } catch (IOException e) {
                log.error("Failed to write Tmap request payload to file", e);
            }
        } catch (Exception e) {
            log.warn("Failed to serialize request body for logging", e);
        }

        return webClient.post()
                .uri("/tmap/routes/prediction?version=1")
                .header("appKey", tmapApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> status.isError(), response ->
                        response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Tmap API request failed with status code: {} and body: {}", response.statusCode(), errorBody);
                                    return Mono.error(new RuntimeException("Tmap API request failed."));
                                })
                )
                .bodyToMono(String.class)
                .flatMap(responseBody -> {
                    try {
                        log.info("Tmap API Response: {}", responseBody);
                        try (FileWriter file = new FileWriter("tmap_response_payload.json")) {
                            file.write(responseBody);
                        } catch (IOException e) {
                            log.error("Failed to write Tmap response payload to file", e);
                        }
                        TmapRouteResponse tmapResponse = objectMapper.readValue(responseBody, TmapRouteResponse.class);
                        return Mono.just(tmapResponse);
                    } catch (Exception e) {
                        log.error("Error parsing Tmap response: {}", e.getMessage());
                        return Mono.error(new RuntimeException("Error parsing Tmap response.", e));
                    }
                });
    }
}

