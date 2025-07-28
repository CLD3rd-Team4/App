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
                .doOnError(error -> log.error("Error calling Kakao API", error));
    }
}
