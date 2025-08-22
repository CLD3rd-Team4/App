
package com.mapzip.recommend.grpc;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapzip.recommend.config.GrpcHeaderConfig;
import com.mapzip.recommend.dto.MultiSlotRecommendRequestDto;
import com.mapzip.recommend.dto.SlotInfoDto;
import com.mapzip.recommend.entity.RecommendationSelectionEntity;
import com.mapzip.recommend.repository.RecommendationSelectionRepository;
import com.mapzip.recommend.service.RecommendRequestService;
import com.mapzip.recommend.service.ReviewClientService;
import com.mapzip.recommend.service.ScheduleDetailQueryService;

import io.grpc.stub.StreamObserver;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

@Slf4j
@RequiredArgsConstructor
@GrpcService
public class RecommendServiceImpl extends RecommendServiceGrpc.RecommendServiceImplBase {

	private final RecommendRequestService recommendRequestService;
	private final RecommendationSelectionRepository selectionRepository;
	private final RedisTemplate<String, String> redisTemplate;
	private final ReviewClientService reviewClientService;
	private final ScheduleDetailQueryService scheduleDetailQueryService;
	private final StringRedisTemplate redisTemplateString;
    private final RecommendationSelectionRepository selectionRepo;


	// 선택된 스케줄 프론트에서 조회
    @Override
    public void getScheduleDetail(GetSelectedScheduleDetailRequest request,
            StreamObserver<GetSelectedScheduleDetailResponse> responseObserver) {

        final String scheduleId = request.getScheduleId();
        final String userId = GrpcHeaderConfig.UserIdContext.USER_ID.get();

        try {
            var snapOpt = scheduleDetailQueryService.get(userId, scheduleId);
            if (snapOpt.isEmpty()) {
                responseObserver.onNext(GetSelectedScheduleDetailResponse.newBuilder()
                        .setStatus("NOT_FOUND").setMessage("No schedule detail in Redis").build());
                responseObserver.onCompleted();
                return;
            }

            var s = snapOpt.get();

            // updateLocs(JSON) → repeated UpdateEvent
            java.util.List<UpdateEvent> updates = new java.util.ArrayList<>();
            try {
                String raw = s.getUpdateLocs(); // JSON 배열 문자열 (null 가능)
                if (raw != null && !raw.isBlank()) {
                    java.lang.reflect.Type listType =
                        new com.google.gson.reflect.TypeToken<java.util.List<java.util.Map<String, String>>>() {}.getType();
                    java.util.List<java.util.Map<String, String>> arr = new com.google.gson.Gson().fromJson(raw, listType);
                    if (arr != null) {
                        for (java.util.Map<String, String> m : arr) {
                            double lat = parseDoubleSafe(m.get("lat"));
                            double lng = parseDoubleSafe(m.get("lng"));
                            String time = normalizeKoreanAmPm(nvl(m.get("time"))); // ★ 조회 시도 변환

                            UpdateEvent.Builder ub = UpdateEvent.newBuilder();
                            if (!Double.isNaN(lat)) ub.setLat(lat);
                            if (!Double.isNaN(lng)) ub.setLng(lng);
                            if (!time.isBlank())   ub.setTime(time);

                            updates.add(ub.build());
                        }
                    }
                }
            } catch (Exception ignore) {}

            ScheduleDetail detail = ScheduleDetail.newBuilder()
                    .setDepartureTime(nvl(s.getDepartureTime()))
                    .setDepartureName(nvl(s.getDepartureName()))
                    .setDestinationName(nvl(s.getDestinationName()))
                    .setEstimatedArrivalTime(nvl(s.getEstimatedArrivalTime()))
                    .addAllWaypointNames(s.getWaypointNames() == null ? java.util.List.of() : s.getWaypointNames())
                    .addAllWaypointTimes(s.getWaypointTimes() == null ? java.util.List.of() : s.getWaypointTimes())
                    .addAllUpdates(updates)
                    .build();

            responseObserver.onNext(GetSelectedScheduleDetailResponse.newBuilder()
                    .setStatus("OK").setMessage("success").setScheduleDetail(detail).build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("getScheduleDetail error (userId={}, scheduleId={})", userId, scheduleId, e);
            responseObserver.onNext(GetSelectedScheduleDetailResponse.newBuilder()
                    .setStatus("ERROR").setMessage("Internal error").build());
            responseObserver.onCompleted();
        }
    }


    private static double parseDoubleSafe(String s) {
        if (s == null || s.isBlank()) return Double.NaN;
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return Double.NaN; }
    }

    // 저장 포맷과 호환: 이미 "오전/오후 HH:mm"이면 그대로, "HH:mm"도 오전/오후로, ISO면 변환
    private static String normalizeKoreanAmPm(String input) {
        if (input == null || input.isBlank()) return "";
        if (input.matches("(오전|오후)\\s*\\d{1,2}:\\d{2}")) return input.trim();
        if (input.matches("\\d{1,2}:\\d{2}")) {
            String[] sp = input.split(":");
            int h = Integer.parseInt(sp[0]);
            String mm = sp[1];
            String period = (h >= 12) ? "오후" : "오전";
            int display = (h == 0) ? 12 : (h > 12 ? h - 12 : h);
            return period + " " + display + ":" + mm;
        }
        try {
            java.time.Instant inst = java.time.Instant.parse(input.trim());
            java.time.ZonedDateTime zdt = inst.atZone(java.time.ZoneId.of("Asia/Seoul"));
            int h = zdt.getHour();
            int m = zdt.getMinute();
            String period = (h >= 12) ? "오후" : "오전";
            int display = (h == 0) ? 12 : (h > 12 ? h - 12 : h);
            String mm = String.format("%02d", m);
            return period + " " + display + ":" + mm;
        } catch (Exception ignored) {
            return input.trim();
        }
    }

	// 프론트에서 추천 요청
	@Override
	public void sendRecommendRequest(RecommendRequest request, StreamObserver<RecommendResponse> responseObserver) {
		// 추천 처리 로직 호출
		recommendRequestService.sendRecommendRequest(request);

		// 응답
		RecommendResponse response = RecommendResponse.newBuilder().setStatus("OK").setMessage("스케줄 선택이 성공적으로 처리되었습니다.")
				.build();

		responseObserver.onNext(response);
		responseObserver.onCompleted();
	}

	@Override
	@Transactional  
	public void submitSelectedPlace(SelectedPlaceRequest request, StreamObserver<SubmitResponse> responseObserver) {
		//동일 유저 다른 스케줄에서 선택한 식당 정보 db에서 삭제 
		String userId = GrpcHeaderConfig.UserIdContext.USER_ID.get();
		selectionRepository.deleteByUserId(userId);
		
		// 요청에서 유저 및 스케줄 정보 추출
		String scheduleId = request.getScheduleId();
		List<RecommendationSelectionEntity> savedEntities = new ArrayList<>();
		for (SelectedPlace place : request.getSelectedPlacesList()) {
			log.info("Saving place: slotId={}, id={}, name={}", 
			        place.getSlotId(), place.getId(), place.getPlaceName());

			RecommendationSelectionEntity entity = RecommendationSelectionEntity.builder().userId(userId)
					.scheduleId(scheduleId).slotId(place.getSlotId()).placeId(place.getId())
					.placeName(place.getPlaceName()).mealType(place.getMealType())
					.scheduledTime(place.getScheduledTime()).reason(place.getReason()).distance(place.getDistance())
					.addressName(place.getAddressName()).placeUrl(place.getPlaceUrl()).selectedDate(LocalDate.now())
					.averageRating(place.getAverageRating()).representativeReview(place.getRepresentativeReview())
					.build();
			selectionRepository.save(entity);
			savedEntities.add(entity);
		}

		// 리뷰 서비스에 미작성 리뷰로 저장 요청
		try {
			reviewClientService.storePlacesForReview(userId, savedEntities);
			log.info("Successfully stored places for review for user: {}", userId);
		} catch (Exception e) {
			log.error("Failed to store places for review, but continuing with response for user: {}", userId, e);
			// 리뷰 서버 연동 실패해도 추천 저장은 성공으로 처리
		}

		SubmitResponse response = SubmitResponse.newBuilder().setStatus("OK").setMessage("✅ 선택된 식당들이 성공적으로 저장되었습니다.").build();

		responseObserver.onNext(response);
		responseObserver.onCompleted();
	}
	
	@Override
	public void getRecommendationResults(GetRecommendationResultsRequest request,
	        StreamObserver<GetRecommendationResultsResponse> responseObserver) {

	    final String userId = GrpcHeaderConfig.UserIdContext.USER_ID.get();
	    final String scheduleId = request.getScheduleId();
	    final String clientRunId = request.getRunId(); // 요청 runId

	    // ★ Valkey에 저장된 마지막 runId 불러오기
	    final String lastRunKey = "recommend:run:last:" + scheduleId; // scheduleId 단위 키
	    final String lastCompletedRunId = redisTemplateString.opsForValue().get(lastRunKey);

	    // ★ runId가 왔는데, 최신 runId와 다르면 무조건 PENDING 반환 (이전 결과로 OK 떨어지는 것 방지)
	    if (clientRunId != null) {
	        if (lastCompletedRunId == null || !clientRunId.equals(lastCompletedRunId)) {
	            GetRecommendationResultsResponse pending = GetRecommendationResultsResponse.newBuilder()
	                    .addAllSlotRecommendations(Collections.emptyList())
	                    .addAllSelectedSlotPlaces(Collections.emptyList())
	                    .setStatus("PENDING")
	                    .setMessage("최신 실행(runId)의 결과가 아직 준비되지 않았습니다.")
	                    .build();
	            responseObserver.onNext(pending);
	            responseObserver.onCompleted();
	            return;
	        }
	    }

	    // 0) DB에서 '이미 선택한 식당' 조회 (userId + scheduleId)
	    List<RecommendationSelectionEntity> selectedRows =
	            selectionRepo.findByUserIdAndScheduleId(userId, scheduleId);

	    // slotId -> Set<placeId> (후보 중복 제거용)
	    Map<String, Set<String>> selectedIdsBySlot = new HashMap<>();
	    // slotId -> List<PlaceInfo> (응답으로 보낼 '이전선택')
	    Map<String, List<PlaceInfo>> selectedPlacesBySlot = new HashMap<>();

	    for (RecommendationSelectionEntity row : selectedRows) {
	        String slotId = nvl(row.getSlotId());
	        String placeId = nvl(row.getPlaceId());

	        selectedIdsBySlot.computeIfAbsent(slotId, k -> new HashSet<>()).add(placeId);

	        PlaceInfo selectedPi = PlaceInfo.newBuilder()
	                .setId(placeId)
	                .setPlaceName(nvl(row.getPlaceName()))
	                .setReason(nvl(row.getReason()))
	                .setDistance(nvl(row.getDistance()))
	                .setScheduledTime(nvl(row.getScheduledTime()))
	                .setMealType(Optional.ofNullable(row.getMealType()).orElse(0))
	                .setPlaceUrl(nvl(row.getPlaceUrl()))
	                .setAddressName(nvl(row.getAddressName()))
	                .setAverageRating(Optional.ofNullable(row.getAverageRating()).orElse(0d))
	                .setRepresentativeReview(nvl(row.getRepresentativeReview()))
	                .build();

	        selectedPlacesBySlot.computeIfAbsent(slotId, k -> new ArrayList<>()).add(selectedPi);
	    }

	    // 1) Redis 후보 로딩 (키 포맷: recommend:{userId}:{scheduleId}:{slotId}:{MEAL|SNACK}:{placeN})
	    final String redisKeyPattern = String.format("recommend:%s:%s:*:*:place*", userId, scheduleId);
	    Set<String> keys = redisTemplateString.keys(redisKeyPattern);

	    // 2) Redis -> Slot별 후보 목록(PlaceWithOrder) 구성 + DB 중복 제거
	    class PlaceWithOrder {
	        final PlaceInfo place; final int order;
	        PlaceWithOrder(PlaceInfo p, int o) { this.place = p; this.order = o; }
	    }

	    Map<String, List<PlaceWithOrder>> slotMap = new HashMap<>();
	    ObjectMapper objectMapper = new ObjectMapper();

	    List<String> sortedKeys = new ArrayList<>(keys == null ? List.of() : keys);
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
	            String placeToken = parts[5];    // place3

	            int placeOrder = 0;
	            try {
	                placeOrder = Integer.parseInt(placeToken.replaceFirst("place", ""));
	            } catch (NumberFormatException ignore) {}

	            int mealTypeFromKey = "MEAL".equalsIgnoreCase(mealTypeToken) ? 0
	                    : "SNACK".equalsIgnoreCase(mealTypeToken) ? 1 : 0;

	            JsonNode node = objectMapper.readTree(value);
	            String id = node.path("id").asText("");

	            // DB에서 이미 선택된 place는 후보에서 제거
	            if (selectedIdsBySlot.getOrDefault(slotId, Set.of()).contains(id)) {
	                continue;
	            }

	            int mealType = node.has("mealType")
	                    ? node.path("mealType").asInt(mealTypeFromKey)
	                    : mealTypeFromKey;

	            PlaceInfo place = PlaceInfo.newBuilder()
	                    .setId(id)
	                    .setPlaceName(node.path("placeName").asText(""))
	                    .setReason(node.path("reason").asText(""))
	                    .setDistance(node.path("distance").asText(""))
	                    .setScheduledTime(node.path("scheduledTime").asText(""))
	                    .setMealType(mealType)
	                    .setPlaceUrl(node.path("placeUrl").asText(""))
	                    .setAddressName(node.path("addressName").asText(""))
	                    .setAverageRating(node.path("averageRating").asDouble(0))
	                    .setRepresentativeReview(node.path("representativeReview").asText(""))
	                    .build();

	            slotMap.computeIfAbsent(slotId, k -> new ArrayList<>())
	                   .add(new PlaceWithOrder(place, placeOrder));

	        } catch (Exception e) {
	            log.warn("❌ Redis 값 파싱 오류 - key: {}", key, e);
	        }
	    }

	    // 3) 후보/이전선택을 SlotRecommendation으로 변환
	    List<SlotRecommendation> candidateSlots = slotMap.entrySet().stream()
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

	    List<SlotRecommendation> selectedSlots = selectedPlacesBySlot.entrySet().stream()
	            .map(e -> SlotRecommendation.newBuilder()
	                    .setSlotId(e.getKey())
	                    .addAllPlaces(e.getValue())
	                    .build())
	            .toList();

	    // 4) 응답 (runId 포함)
	    boolean hasAny = !candidateSlots.isEmpty() || !selectedSlots.isEmpty();

	    GetRecommendationResultsResponse response = GetRecommendationResultsResponse.newBuilder()
	            .addAllSlotRecommendations(candidateSlots)   // 새 후보
	            .addAllSelectedSlotPlaces(selectedSlots)     // 이전 선택
	            .setStatus(hasAny ? "OK" : "PENDING")
	            .setMessage(hasAny ? "추천 결과를 성공적으로 불러왔습니다."
	                               : "추천 결과가 아직 준비되지 않았습니다.")
	            .build();

	    responseObserver.onNext(response);
	    responseObserver.onCompleted();
	}

	private static String nvl(String s) {
	    return s == null ? "" : s;
	}




	@Override
	public void getSelectedPlaces(GetSelectedPlacesRequest request,
			StreamObserver<GetSelectedPlacesResponse> responseObserver) {
		String userId = request.getUserId();
		String scheduleId = request.getScheduleId();

		// DB에서 유저 및 스케줄 기준으로 데이터 조회
		List<RecommendationSelectionEntity> selectedList = selectionRepository.findByUserIdAndScheduleId(userId,
				scheduleId);

		if (selectedList == null || selectedList.isEmpty()) {
			GetSelectedPlacesResponse response = GetSelectedPlacesResponse.newBuilder().setStatus("NOT_FOUND")
					.setMessage("선택된 식당이 없습니다.").build();

			responseObserver.onNext(response);
			responseObserver.onCompleted();
			return;
		}

		Map<String, List<PlaceInfo>> slotMap = new HashMap<>();

		for (RecommendationSelectionEntity entity : selectedList) {
			PlaceInfo place = PlaceInfo.newBuilder().setId(entity.getPlaceId()).setPlaceName(entity.getPlaceName())
					.setReason(entity.getReason()).setDistance(entity.getDistance())
					.setScheduledTime(entity.getScheduledTime()).setMealType(entity.getMealType())
					.setPlaceUrl(entity.getPlaceUrl()).setAddressName(entity.getAddressName())
					.setAverageRating(entity.getAverageRating())
					.setRepresentativeReview(entity.getRepresentativeReview()).build();

			slotMap.computeIfAbsent(entity.getSlotId(), k -> new ArrayList<>()).add(place);
		}

		// SlotRecommendation 리스트로 변환
		List<SlotSelectedPlaces> slotPlaces = slotMap.entrySet().stream().map(entry -> SlotSelectedPlaces.newBuilder()
				.setSlotId(entry.getKey()).addAllPlaces(entry.getValue()).build()).collect(Collectors.toList());

		// 최종 응답 생성
		GetSelectedPlacesResponse response = GetSelectedPlacesResponse.newBuilder().addAllSlotPlaces(slotPlaces)
				.setStatus("OK").setMessage("성공적으로 선택된 식당을 불러왔습니다.").build();

		responseObserver.onNext(response);
		responseObserver.onCompleted();
	}

	// to 리뷰 서버
	@Override
	public void placesForReview(PlacesForReviewRequest request,
			StreamObserver<PlacesForReviewResponse> responseObserver) {

		// 0.userid 가져오
		String userId = GrpcHeaderConfig.UserIdContext.USER_ID.get();

		// 1. 유저의 추천 선택 식당 조회
		List<RecommendationSelectionEntity> selectedList = selectionRepository.findByUserId(userId);

		if (selectedList == null || selectedList.isEmpty()) {
			PlacesForReviewResponse response = PlacesForReviewResponse.newBuilder().setStatus("NOT_FOUND")
					.setMessage("리뷰를 위한 식당 선택 정보가 없습니다.").build();
			responseObserver.onNext(response);
			responseObserver.onCompleted();
			return;
		}

		// 2. 응답할 식당 리스트 (이미 응답한 건 제외)
		List<ReviewPlaceInfo> responsePlaces = new ArrayList<>();

		for (RecommendationSelectionEntity entity : selectedList) {
			String placeId = entity.getPlaceId();
			String redisKey = "review_sent:" + userId + ":" + placeId;

			Boolean alreadySent = redisTemplate.hasKey(redisKey);
			if (Boolean.TRUE.equals(alreadySent)) {
				log.info("⚠️ 이미 리뷰 응답한 식당 - placeId: {}", placeId);
				continue; // 건너뛰기
			}
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
			// 아직 응답하지 않은 경우 → 응답 리스트에 포함
			ReviewPlaceInfo reviewPlaceInfo = ReviewPlaceInfo.newBuilder().setId(placeId)
					.setAddressName(entity.getAddressName()).setPlaceUrl(entity.getPlaceUrl())
					.setScheduledTime(entity.getSelectedDate().format(formatter)).build();

			responsePlaces.add(reviewPlaceInfo);

			// 응답 보냈다고 Redis에 기록
			redisTemplate.opsForValue().set(redisKey, "true", Duration.ofHours(24));
		}

		// 3. 응답 생성
		PlacesForReviewResponse response = PlacesForReviewResponse.newBuilder().addAllPlaces(responsePlaces)
				.setStatus("OK").setMessage("리뷰 서버로 응답이 성공적으로 전달되었습니다.").build();

		responseObserver.onNext(response);
		responseObserver.onCompleted();
	}

}