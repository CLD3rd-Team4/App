package com.mapzip.schedule.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapzip.schedule.client.KakaoClient;
import com.mapzip.schedule.dto.kakao.KakaoSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

// [수정] DB 관련 의존성 및 어노테이션 제거
@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoApiService {

    private final KakaoClient kakaoClient;
    private final ObjectMapper objectMapper;

    /**
     * 단일 위치 정보를 기반으로 Kakao API를 호출하고,
     * 식당 검색 결과를 파일로 저장합니다.
     *
     * @param slotId 처리할 식사 시간 슬롯의 ID
     * @param lat 위도
     * @param lon 경도
     * @param radius 반경
     */
    public void processAndSaveSingleRestaurantSuggestion(String slotId, double lat, double lon, int radius) {
        // Kakao API 호출 (비동기 -> 동기 변환)
        KakaoSearchResponse kakaoResponse = kakaoClient.searchRestaurants(lat, lon, radius).block();

        if (kakaoResponse != null) {
            // 파일 저장
            saveResponseToFile(kakaoResponse, slotId).block(); // 동기적으로 완료 대기
        } else {
            log.warn("Kakao API response was null for slot {}", slotId);
        }
    }

    /**
     * Kakao API 응답을 JSON 파일로 저장합니다.
     * @return 파일 저장 성공 시 Mono<Void>, 실패 시 Mono.error
     */
    private Mono<Void> saveResponseToFile(KakaoSearchResponse kakaoResponse, String slotId) {
        return Mono.fromRunnable(() -> {
            try {
                String fileName = "kakao_response_" + slotId + ".json";
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(new java.io.File(fileName), kakaoResponse);
                log.info("Kakao API response for slot {} saved to {}", slotId, fileName);
            } catch (Exception fileEx) {
                log.error("Failed to save Kakao API response to file for slot {}", slotId, fileEx);
                throw new RuntimeException(fileEx);
            }
        });
    }
}
