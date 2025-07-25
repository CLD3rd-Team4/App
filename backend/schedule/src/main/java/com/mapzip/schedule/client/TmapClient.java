package com.mapzip.schedule.client;

import com.mapzip.schedule.dto.TmapRouteRequest;
import com.mapzip.schedule.dto.TmapRouteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // 추가
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import com.fasterxml.jackson.databind.ObjectMapper; // 추가

@Slf4j
@Component
public class TmapClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public TmapClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Value("${external.api.tmap.url}")
    private String tmapApiUrl;

    @Value("${external.api.tmap.key}")
    private String tmapApiKey;

    public TmapRouteResponse getRoutePrediction(TmapRouteRequest requestBody) {
        // 요청 본문 로깅 및 파일 저장
        try {
            String jsonPayload = objectMapper.writeValueAsString(requestBody);
            log.info("TMap API Request Payload: {}", jsonPayload);

            // 파일에 요청 페이로드 저장
            try (java.io.FileWriter file = new java.io.FileWriter("tmap_request_payload.json")) {
                file.write(jsonPayload);
            } catch (java.io.IOException e) {
                log.error("Failed to write Tmap request payload to file", e);
            }

        } catch (Exception e) {
            log.warn("Failed to serialize request body for logging", e);
        }

        URI uri = UriComponentsBuilder
                .fromHttpUrl(tmapApiUrl)
                .path("/tmap/routes/prediction")
                .queryParam("version", 1)
                .encode()
                .build()
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("appKey", tmapApiKey);
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);

        HttpEntity<TmapRouteRequest> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(uri, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                // 응답 본문을 파일에 저장
                try (java.io.FileWriter file = new java.io.FileWriter("tmap_response_payload.json")) {
                    file.write(response.getBody());
                } catch (java.io.IOException e) {
                    log.error("Failed to write Tmap response payload to file", e);
                }

                // 수동으로 JSON을 객체로 변환
                TmapRouteResponse tmapResponse = objectMapper.readValue(response.getBody(), TmapRouteResponse.class);
                return tmapResponse;

            } else {
                throw new RuntimeException("Tmap API request failed with status code: " + response.getStatusCode() + " and body: " + response.getBody());
            }
        } catch (Exception e) {
            throw new RuntimeException("Error calling Tmap API: " + e.getMessage(), e);
        }
    }
}

