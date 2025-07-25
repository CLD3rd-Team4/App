package com.mapzip.schedule.client;

import com.mapzip.schedule.dto.TmapRouteRequest;
import com.mapzip.schedule.dto.TmapRouteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component
@RequiredArgsConstructor
public class TmapClient {

    private final RestTemplate restTemplate;

    @Value("${external.api.tmap.url}")
    private String tmapApiUrl;

    @Value("${external.api.tmap.key}")
    private String tmapApiKey;

    public TmapRouteResponse getRoutePrediction(TmapRouteRequest requestBody) {
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
            ResponseEntity<TmapRouteResponse> response = restTemplate.postForEntity(uri, entity, TmapRouteResponse.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            } else {
                throw new RuntimeException("Tmap API request failed with status code: " + response.getStatusCode() + " and body: " + response.getBody());
            }
        } catch (Exception e) {
            throw new RuntimeException("Error calling Tmap API: " + e.getMessage(), e);
        }
    }
}
