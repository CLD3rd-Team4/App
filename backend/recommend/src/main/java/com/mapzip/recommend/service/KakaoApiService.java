package com.mapzip.recommend.service;

import com.mapzip.recommend.client.KakaoClient;
import com.mapzip.recommend.dto.kakao.KakaoSearchResponse;
import com.mapzip.recommend.dto.MultiSlotRecommendRequestDto;
import com.mapzip.recommend.dto.SlotInfoDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoApiService {

    private final KakaoClient kakaoClient;

    public Map<String, KakaoSearchResponse> getSlotRestaurantMap(MultiSlotRecommendRequestDto requestDto) {
        Map<String, KakaoSearchResponse> resultMap = new HashMap<>();

        for (SlotInfoDto slot : requestDto.getSlots()) {
            try {
                KakaoSearchResponse response = kakaoClient
                        .searchRestaurants(slot.getLat(), slot.getLon(), slot.getRadius())
                        .block(); // Mono → blocking (나중에 비동기로 바꿀 수도 있음)

                if (response != null) {
                    resultMap.put(slot.getSlotId(), response);
                } else {
                    log.warn("Kakao 응답이 null입니다. slotId: {}", slot.getSlotId());
                }
            } catch (Exception e) {
                log.error("Kakao 호출 실패 - slotId: {}, error: {}", slot.getSlotId(), e.getMessage());
            }
        }

        return resultMap;
    }
}
