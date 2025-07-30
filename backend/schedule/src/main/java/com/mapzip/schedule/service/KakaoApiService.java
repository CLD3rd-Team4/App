package com.mapzip.schedule.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapzip.schedule.client.KakaoClient;
import com.mapzip.schedule.dto.kakao.KakaoSearchResponse;
import com.mapzip.schedule.entity.MealTimeSlot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoApiService {

    private final KakaoClient kakaoClient;
    private final ObjectMapper objectMapper;

    /**
     * 계산된 위치 목록을 기반으로 Kakao API를 호출하고,
     * 각 위치에 대한 식당 검색 결과를 처리 및 파일로 저장합니다.
     *
     * @param calculatedLocations 계산된 식사 시간 위치 정보 리스트
     * @param mealTimeSlotEntities 해당 스케줄의 식사 시간 슬롯 엔티티 리스트
     */
    public void processAndSaveRestaurantSuggestions(
            List<RouteService.CalculatedLocation> calculatedLocations,
            List<MealTimeSlot> mealTimeSlotEntities
    ) {
        Flux.fromIterable(calculatedLocations)
                .flatMap(location -> {
                    MealTimeSlot slot = findSlotForLocation(mealTimeSlotEntities, location.getSlotId());
                    if (slot == null) {
                        return Mono.empty();
                    }
                    // Kakao API 호출
                    return kakaoClient.searchRestaurants(location.getLat(), location.getLon(), slot.getRadius())
                            .flatMap(kakaoResponse -> {
                                // DB 엔티티 업데이트 (좌표 정보만)
                                updateSlotWithLocation(slot, location);
                                // 파일 저장 로직
                                return saveResponseToFile(kakaoResponse, slot);
                            });
                })
                .collectList()
                .block(); // 모든 비동기 작업이 완료될 때까지 대기
    }

    /**
     * Slot ID로 해당하는 MealTimeSlot 엔티티를 찾습니다.
     */
    private MealTimeSlot findSlotForLocation(List<MealTimeSlot> slots, String slotId) {
        return slots.stream()
                .filter(s -> s.getId().equals(slotId))
                .findFirst()
                .orElse(null);
    }

    /**
     * MealTimeSlot 엔티티에 계산된 위치 정보를 JSON 형태로 업데이트합니다.
     */
    private void updateSlotWithLocation(MealTimeSlot slot, RouteService.CalculatedLocation location) {
        try {
            Map<String, Object> locationJson = new HashMap<>();
            locationJson.put("lat", location.getLat());
            locationJson.put("lon", location.getLon());
            locationJson.put("scheduled_time", slot.getScheduledTime());
            slot.setCalculatedLocation(objectMapper.writeValueAsString(locationJson));
        } catch (Exception e) {
            log.error("Failed to serialize location for slot {}", slot.getId(), e);
        }
    }

    /**
     * Kakao API 응답을 JSON 파일로 저장합니다.
     * @return 파일 저장 성공 시 Mono<Void>, 실패 시 Mono.error
     */
    private Mono<Void> saveResponseToFile(KakaoSearchResponse kakaoResponse, MealTimeSlot slot) {
        try {
            String fileName = "kakao_response_" + slot.getId() + ".json";
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new java.io.File(fileName), kakaoResponse);
            log.info("Kakao API response for slot {} saved to {}", slot.getId(), fileName);
            return Mono.empty(); // 성공 시 빈 Mono 반환
        } catch (Exception fileEx) {
            log.error("Failed to save Kakao API response to file for slot {}", slot.getId(), fileEx);
            return Mono.error(fileEx); // 실패 시 에러 전파
        }
    }
}
