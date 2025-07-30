package com.mapzip.schedule.client;

import com.mapzip.schedule.dto.kakao.KakaoSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class KakaoClient {

    private final WebClient webClient;
    private final String kakaoApiKey;

    public KakaoClient(WebClient.Builder webClientBuilder,
                       @Value("${external.api.kakao.url}") String kakaoApiUrl,
                       @Value("${external.api.kakao.key}") String kakaoApiKey) {
        this.webClient = webClientBuilder.baseUrl(kakaoApiUrl).build();
        this.kakaoApiKey = kakaoApiKey;
    }

    public Mono<KakaoSearchResponse> searchRestaurants(double latitude, double longitude, int radius) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/local/search/category.json")
                        .queryParam("category_group_code", "FD6")
                        .queryParam("x", longitude)
                        .queryParam("y", latitude)
                        .queryParam("radius", radius)
                        .queryParam("sort", "distance")
                        .queryParam("size", 15)
                        .build())
                .header("Authorization", "KakaoAK " + kakaoApiKey)
                .retrieve()
                .onStatus(status -> status.isError(), response ->
                        response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Kakao API request failed with status code: {} and body: {}", response.statusCode(), errorBody);
                                    return Mono.error(new RuntimeException("Failed to fetch data from Kakao API."));
                                })
                )
                .bodyToMono(KakaoSearchResponse.class)
                /*
                // 응답을 String으로 받아서 파일에 저장하는 예시
                .bodyToMono(String.class)
                .doOnSuccess(responseBody -> {
                    try {
                        java.nio.file.Path path = java.nio.file.Paths.get("kakao_response_" + System.currentTimeMillis() + ".json");
                        java.nio.file.Files.writeString(path, responseBody);
                        log.info("Kakao API response saved to file: {}", path);
                    } catch (java.io.IOException e) {
                        log.error("Failed to save Kakao API response to file", e);
                    }
                })
                // 실제 반환 타입인 KakaoSearchResponse로 변환
                .map(responseBody -> {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        return objectMapper.readValue(responseBody, KakaoSearchResponse.class);
                    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                        throw new RuntimeException("Failed to parse Kakao API response", e);
                    }
                })
                */
                .doOnError(error -> log.error("Error calling Kakao API", error));
    }
}
