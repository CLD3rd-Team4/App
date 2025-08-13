package com.mapzip.recommend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.util.JsonFormat;
import com.mapzip.recommend.grpc.GetRecommendationResultsResponse;
import com.mapzip.recommend.grpc.PlaceInfo;
import com.mapzip.recommend.grpc.RecommendResponse;
import com.mapzip.recommend.grpc.SelectedPlace;
import com.mapzip.recommend.grpc.SelectedPlaceRequest;
import com.mapzip.recommend.grpc.SlotRecommendation;
import com.mapzip.recommend.grpc.SubmitResponse;
import com.mapzip.recommend.service.RecommendRequestService;
import com.mapzip.recommend.entity.RecommendationSelectionEntity;
import com.mapzip.recommend.repository.RecommendationSelectionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDate;
import java.util.*;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOriginPatterns("*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }

    @RestController
    @RequiredArgsConstructor
    @Slf4j
    static class LocalRestBridgeController {

        private final RecommendRequestService recommendRequestService;
        private final RedisTemplate<String, String> redisTemplate;
        private final RecommendationSelectionRepository selectionRepository;

        private final JsonFormat.Printer printer = JsonFormat.printer()
                .includingDefaultValueFields()
                .preservingProtoFieldNames();

        private final JsonFormat.Parser parser = JsonFormat.parser()
                .ignoringUnknownFields();

        // === 기존: 요청 트리거 ===
        @PostMapping(value = "/recommend/request", produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<String> sendRecommendRequest(@RequestParam String scheduleId) throws Exception {
            recommendRequestService.sendRecommendRequest(scheduleId);

            RecommendResponse res = RecommendResponse.newBuilder()
                    .setStatus("OK")
                    .setMessage("스케줄 선택이 성공적으로 처리되었습니다.")
                    .build();

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(printer.print(res));
        }

        // === 기존: 결과 조회 ===
        @GetMapping(value = "/recommend/result", produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<String> getRecommendationResults(
                @RequestParam String userId,
                @RequestParam String scheduleId
        ) throws Exception {

            final String redisKeyPattern = String.format("recommend:%s:%s:*:*:place*", userId, scheduleId);
            Set<String> keys = redisTemplate.keys(redisKeyPattern);

            if (keys == null || keys.isEmpty()) {
                GetRecommendationResultsResponse pending = GetRecommendationResultsResponse.newBuilder()
                        .setStatus("PENDING")
                        .setMessage("추천 결과가 아직 준비되지 않았습니다.")
                        .build();
                return ResponseEntity.ok(printer.print(pending));
            }

            class PlaceWithOrder {
                final PlaceInfo place;
                final int order;
                PlaceWithOrder(PlaceInfo p, int o) { this.place = p; this.order = o; }
            }

            Map<String, List<PlaceWithOrder>> slotMap = new HashMap<>();
            ObjectMapper objectMapper = new ObjectMapper();

            List<String> sortedKeys = new ArrayList<>(keys);
            Collections.sort(sortedKeys);

            for (String key : sortedKeys) {
                String value = redisTemplate.opsForValue().get(key);
                if (value == null) continue;

                try {
                    String[] parts = key.split(":");
                    if (parts.length < 6) {
                        log.warn("키 형식 불일치: {}", key);
                        continue;
                    }
                    String slotId = parts[3];
                    String mealTypeToken = parts[4]; // MEAL | SNACK
                    String placeToken   = parts[5];  // place3

                    int placeOrder = 0;
                    try { placeOrder = Integer.parseInt(placeToken.replaceFirst("place", "")); }
                    catch (NumberFormatException ignore) {}

                    int mealTypeFromKey =
                            "MEAL".equalsIgnoreCase(mealTypeToken)  ? 0 :
                            "SNACK".equalsIgnoreCase(mealTypeToken) ? 1 : -1;

                    JsonNode node = objectMapper.readTree(value);

                    int mealType = node.path("mealType").isMissingNode()
                            ? mealTypeFromKey
                            : node.path("mealType").asInt(mealTypeFromKey);

                    PlaceInfo place = PlaceInfo.newBuilder()
                            .setId(node.path("id").asText(""))
                            .setPlaceName(node.path("placeName").asText(""))
                            .setReason(node.path("reason").asText(""))
                            .setDistance(node.path("distance").asText(""))
                            .setScheduledTime(node.path("scheduledTime").asText(""))
                            .setMealType(mealType >= 0 ? mealType : 0)
                            .setPlaceUrl(node.path("placeUrl").asText(""))
                            .setAverageRating(node.path("averageRating").asDouble(0))
                            .setAddressName(node.path("addressName").asText(""))
                            .setRepresentativeReview(node.path("representativeReview").asText(""))
                            .build();

                    slotMap.computeIfAbsent(slotId, k -> new ArrayList<>())
                           .add(new PlaceWithOrder(place, placeOrder));

                } catch (Exception e) {
                    log.warn("❌ Redis 값 파싱 오류 - key: {}", key, e);
                }
            }

            List<SlotRecommendation> slotRecommendations = slotMap.entrySet().stream()
                    .map(entry -> {
                        List<PlaceInfo> placesSorted = entry.getValue().stream()
                                .sorted(Comparator.comparingInt(p -> p.order))
                                .map(p -> p.place)
                                .toList();
                        return SlotRecommendation.newBuilder()
                                .setSlotId(entry.getKey())
                                .addAllPlaces(placesSorted)
                                .build();
                    })
                    .toList();

            GetRecommendationResultsResponse ok = GetRecommendationResultsResponse.newBuilder()
                    .addAllSlotRecommendations(slotRecommendations)
                    .setStatus("OK")
                    .setMessage("추천 결과를 성공적으로 불러왔습니다.")
                    .build();

            return ResponseEntity.ok(printer.print(ok));
        }

        // === 신규: 선택 결과 저장 (gRPC SubmitSelectedPlace와 동일 동작) ===
        // proto 옵션과 동일 경로: post "/recommend/submit"
        @PostMapping(value = "/recommend/submit", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
        @Transactional
        public ResponseEntity<String> submitSelectedPlace(@RequestBody String bodyJson) {
            try {
                // 1) proto JSON → SelectedPlaceRequest로 파싱
                SelectedPlaceRequest.Builder builder = SelectedPlaceRequest.newBuilder();
                parser.merge(bodyJson, builder);
                SelectedPlaceRequest request = builder.build();

                // 2) 동일 유저의 다른 스케줄 선택 정보 전체 삭제
                selectionRepository.deleteByUserId(request.getUserId());

                // 3) 저장
                String userId = request.getUserId();
                String scheduleId = request.getScheduleId();
                for (SelectedPlace place : request.getSelectedPlacesList()) {
                    log.info("Saving place: slotId={}, id={}, name={}",
                            place.getSlotId(), place.getId(), place.getPlaceName());

                    RecommendationSelectionEntity entity = RecommendationSelectionEntity.builder()
                            .userId(userId)
                            .scheduleId(scheduleId)
                            .slotId(place.getSlotId())
                            .placeId(place.getId())
                            .placeName(place.getPlaceName())
                            .mealType(place.getMealType())                // 엔티티 필드 타입: String 또는 Integer에 맞춰 변환 필요시 toString()/int 변환
                            .scheduledTime(place.getScheduledTime())
                            .reason(place.getReason())
                            .distance(place.getDistance())
                            .addressName(place.getAddressName())
                            .placeUrl(place.getPlaceUrl())
                            .selectedDate(LocalDate.now())
                            .averageRating(place.getAverageRating())
                            .representativeReview(place.getRepresentativeReview())
                            .build();

                    selectionRepository.save(entity);
                }

                // 4) gRPC와 동일한 응답 스키마를 JSON으로
                SubmitResponse response = SubmitResponse.newBuilder()
                        .setStatus("OK")
                        .setMessage("✅ 선택된 식당들이 성공적으로 저장되었습니다.")
                        .build();

                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(printer.print(response));

            } catch (Exception e) {
                log.error("❌ submitSelectedPlace 처리 실패", e);
                SubmitResponse error = SubmitResponse.newBuilder()
                        .setStatus("ERROR")
                        .setMessage("선택 저장 중 오류가 발생했습니다: " + e.getMessage())
                        .build();
                try {
                    return ResponseEntity.internalServerError()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(printer.print(error));
                } catch (Exception ignore) {
                    // printer 실패 시 plain text
                    return ResponseEntity.internalServerError().body("{\"status\":\"ERROR\",\"message\":\"internal error\"}");
                }
            }
        }
    }
}

