package com.mapzip.recommend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mapzip.recommend.dto.RecommendRequestDto;
import com.mapzip.recommend.dto.RecommendResultDto;
import com.mapzip.recommend.dto.SlotInfoDto;
import com.mapzip.recommend.dto.kakao.KakaoSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.util.*;


@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendService {

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final ObjectMapper objectMapper;

    public RecommendResultDto recommendProcess(RecommendRequestDto recommendRequestDto) {
        try {
            String prompt = buildPrompt(recommendRequestDto);

            ClaudeMessagesRequest messagesRequest = new ClaudeMessagesRequest(
                    "bedrock-2023-05-31",
                    List.of(new Message("user", prompt)),
                    1000,
                    0.7,
                    0.9
            );

            String body = objectMapper.writeValueAsString(messagesRequest);

            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId("anthropic.claude-3-sonnet-20240229-v1:0")
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(body))
                    .build();

            InvokeModelResponse bedrockResult = bedrockRuntimeClient.invokeModel(request);
            String result = bedrockResult.body().asUtf8String();

            String recommendPlaceListJson = buildRecommendPlaceListJson(
                    recommendRequestDto.getKakaoPlaceList(),
                    result
            );
            return new RecommendResultDto(
                    recommendRequestDto.getUserId(),
                    recommendRequestDto.getScheduleId(),
                    new ArrayList<>(recommendRequestDto.getKakaoPlaceList().keySet()),
                    recommendPlaceListJson,
                    recommendRequestDto.getScheduledTimes()
            );

        } catch (JsonProcessingException e) {
            throw new RuntimeException("❌ Bedrock 요청 JSON 직렬화 실패", e);
        } catch (Exception e) {
            throw new RuntimeException("❌ Bedrock 호출 중 오류", e);
        }
    }

    private String buildPrompt(RecommendRequestDto dto) {
        StringBuilder slotDescriptions = new StringBuilder();
        for (Map.Entry<String, KakaoSearchResponse> entry : dto.getKakaoPlaceList().entrySet()) {
            slotDescriptions.append(String.format("\n[%s 구간]", entry.getKey()));
            try {
                slotDescriptions.append("\n").append(objectMapper.writeValueAsString(entry.getValue()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("❌ KakaoSearchResponse 직렬화 실패", e);
            }
        }

        return String.format("""
        아래는 사용자의 여행 정보입니다.
        목적: %s
        동행자: %s
        참고 메모: %s
        다음은 사용자가 고려하고 있는 음식점 목록입니다 (카카오 장소 API JSON 형식입니다).%s

        사용자는 총 %d개의 시간대에 대해 식당을 추천받고자 합니다.
        아래 형식으로 각 시간대마다 식당을 3개씩 추천하고, 각 식당의 추천 이유를 간단히 작성해 주세요.
        추천은 JSON 형식으로 출력해 주세요.

        ### 출력 형식 예시 ###
        {
          "recommendations": [
            {
              "mealIndex": 1,
              "places": [
                { "id": "1648858596", "reason": "가성비가 좋고 가족 식사에 적합함" },
                { "id": "1639873649", "reason": "간단한 식사로 적절함" },
                { "id": "1920192626", "reason": "거리도 가깝고 리뷰 평점이 높음" }
              ]
            },
            {
              "mealIndex": 2,
              "places": [
                { "id": "688554292", "reason": "현지 음식이 다양하게 제공됨" },
                { "id": "27266866", "reason": "디저트로 적합함" },
                { "id": "1068121745", "reason": "국밥 전문점으로 든든함" }
              ]
            }
          ]
        }

        위 형식을 엄격히 지켜 반드시 JSON만 출력하고, 그 외 문장, 설명, 코드블럭(```json), 안내 메시지를 포함하지 마세요.
        """,
                dto.getPurpose(),
                String.join(", ", dto.getCompanions()),
                dto.getUserNote(),
                slotDescriptions.toString(),
                dto.getKakaoPlaceList().size()
        );
    }

    private String buildRecommendPlaceListJson(Map<String, KakaoSearchResponse> slotPlaceMap, String aiResponseJson) {
        try {
            JsonNode raw = objectMapper.readTree(aiResponseJson);
            String rawText = raw.path("content").get(0).path("text").asText();

            JsonNode aiRoot = objectMapper.readTree(rawText);
            ArrayNode recommendations = (ArrayNode) aiRoot.get("recommendations");

            ObjectNode finalResult = objectMapper.createObjectNode();
            ArrayNode finalRecommendations = objectMapper.createArrayNode();

            for (JsonNode rec : recommendations) {
                int mealIndex = rec.get("mealIndex").asInt();
                ArrayNode places = (ArrayNode) rec.get("places");

                ArrayNode mergedPlaces = objectMapper.createArrayNode();

                for (JsonNode place : places) {
                    String id = place.get("id").asText();
                    String reason = place.get("reason").asText();

                    JsonNode kakaoPlace = slotPlaceMap.values().stream()
                            .flatMap(res -> res.getDocuments().stream())
                            .filter(doc -> doc.getId().equals(id))
                            .findFirst()
                            .map(doc -> {
                                try {
                                    String jsonStr = objectMapper.writeValueAsString(doc);
                                    return objectMapper.readTree(jsonStr);
                                } catch (Exception e) {
                                    throw new RuntimeException("❌ Kakao Document 변환 실패", e);
                                }
                            })
                            .orElse(null);

                    if (kakaoPlace != null) {
                        ObjectNode merged = kakaoPlace.deepCopy();
                        merged.put("reason", reason);
                        mergedPlaces.add(merged);
                    }
                }

                ObjectNode mealObj = objectMapper.createObjectNode();
                mealObj.put("mealIndex", mealIndex);
                mealObj.set("places", mergedPlaces);

                finalRecommendations.add(mealObj);
            }

            finalResult.set("recommendations", finalRecommendations);
            return objectMapper.writeValueAsString(finalResult);

        } catch (Exception e) {
            throw new RuntimeException("🔴 recommendPlaceListJson 생성 중 오류", e);
        }
    }

    record Message(String role, String content) {}

    record ClaudeMessagesRequest(
            String anthropic_version,
            List<Message> messages,
            int max_tokens,
            double temperature,
            double top_p
    ) {}
}
