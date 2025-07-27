package com.mapzip.schedule.client;

import com.mapzip.schedule.dto.kakao.KakaoSearchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component
public class KakaoClient {

    private final RestTemplate restTemplate;
    private final String kakaoApiUrl;
    private final String kakaoApiKey;

    public KakaoClient(RestTemplate restTemplate,
                       @Value("${external.api.kakao.url}") String kakaoApiUrl,
                       @Value("${external.api.kakao.key}") String kakaoApiKey) {
        this.restTemplate = restTemplate;
        this.kakaoApiUrl = kakaoApiUrl;
        this.kakaoApiKey = kakaoApiKey;
    }

    public KakaoSearchResponse searchRestaurants(double latitude, double longitude, int radius) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + kakaoApiKey);

        URI uri = UriComponentsBuilder.fromHttpUrl(kakaoApiUrl + "/v2/local/search/category.json")
                .queryParam("category_group_code", "FD6") // 음식점
                .queryParam("x", longitude)
                .queryParam("y", latitude)
                .queryParam("radius", radius)
                .queryParam("sort", "distance")
                .queryParam("size", 15)
                .build(true)
                .toUri();

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<KakaoSearchResponse> response = restTemplate.exchange(uri, HttpMethod.GET, entity, KakaoSearchResponse.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return response.getBody();
        } else {
            // 간단한 예외 처리, 추후 구체화 필요
            throw new RuntimeException("Failed to fetch data from Kakao API: " + response.getStatusCode());
        }
    }
}
