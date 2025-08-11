package com.mapzip.recommend.service;

import com.mapzip.recommend.client.KakaoClient;
import com.mapzip.recommend.dto.kakao.Document;
import com.mapzip.recommend.dto.kakao.KakaoSearchResponse;
import com.mapzip.recommend.dto.MultiSlotRecommendRequestDto;
import com.mapzip.recommend.dto.ReviewStatsDto;
import com.mapzip.recommend.dto.SlotInfoDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoApiService {

    private final KakaoClient kakaoClient;
    private final ReviewClientService reviewClientService;

    public Map<String, KakaoSearchResponse> getSlotRestaurantMap(MultiSlotRecommendRequestDto requestDto) {
    	Map<String, KakaoSearchResponse> resultMap = new HashMap<String, KakaoSearchResponse>();

        for (SlotInfoDto slot : requestDto.getSlots()) {
            try {
                KakaoSearchResponse response = kakaoClient
                        .searchRestaurants(slot.getLat(), slot.getLon(), slot.getRadius())
                        .block(); // blocking (나중에 비동기 전환 가능)

                if (response == null) {
                    log.warn("Kakao 응답이 null입니다. slotId: {}", slot.getSlotId());
                    continue;
                }

                //  각 식당(Kakao Document)의 id로 리뷰 서버에서 별점/대표리뷰 조회 후 주입
                if (response.getDocuments() != null) {
                    for (Document doc : response.getDocuments()) {
                        if (doc == null || doc.getId() == null) continue;
                        try {
                            ReviewStatsDto stats = reviewClientService.getRestaurantStats(doc.getId());
                            if (stats != null) {
                                doc.setAverageRating(stats.getAverageRating());
                                doc.setRepresentativeReview(stats.getRepresentativeReview());
                            }
                        } catch (Exception e) {
                            log.warn("리뷰 조회 실패 - placeId={}, err={}", doc.getId(), e.toString());
                            // 기본값 주입
                             doc.setAverageRating(0.0);
                             doc.setRepresentativeReview("");
                        }
                    }
                }

                // KakaoSearchResponse 그대로 map에 저장 (리뷰 필드만 추가된 상태)
                resultMap.put(slot.getSlotId(), response);

            } catch (Exception e) {
                log.error("Kakao 호출 실패 - slotId: {}, error: {}", slot.getSlotId(), e.getMessage(), e);
            }
        }

        return resultMap;
    }
}
