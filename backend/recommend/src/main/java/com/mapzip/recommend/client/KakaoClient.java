package com.mapzip.recommend.client;

import com.mapzip.recommend.dto.kakao.KakaoSearchResponse;
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

    // 공통: 카테고리 코드로 조회 (FD6=음식점, CE7=카페 등)
    public Mono<KakaoSearchResponse> searchByCategory(String categoryCode, String lat, String lon, int radius, int size) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/local/search/category.json")
                        .queryParam("category_group_code", categoryCode)
                        .queryParam("x", lon)      
                        .queryParam("y", lat)
                        .queryParam("radius", radius)   
                        .queryParam("sort", "distance") 
                        .queryParam("size", size)    
                        .build())
                .header("Authorization", "KakaoAK " + kakaoApiKey)
                .retrieve()
                .onStatus(status -> status.isError(), response ->
                        response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Kakao API failed: status={}, body={}", response.statusCode(), errorBody);
                                    return Mono.error(new RuntimeException("Failed to fetch data from Kakao API."));
                                })
                )
                .bodyToMono(KakaoSearchResponse.class)
                .doOnError(error -> log.error("Error calling Kakao API", error));
    }

    // 음식점 전용 헬퍼
    public Mono<KakaoSearchResponse> searchRestaurants(String lat, String lon, int radius) {
        return searchByCategory("FD6", lat, lon, radius, 15);
    }

    //  카페 전용 헬퍼
    public Mono<KakaoSearchResponse> searchCafes(String lat, String lon, int radius) {
        return searchByCategory("CE7", lat, lon, radius, 15);
    }
}
