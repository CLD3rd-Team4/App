package com.mapzip.recommend.grpc;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.RedisTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapzip.recommend.config.GrpcHeaderConfig;
import com.mapzip.recommend.dto.MultiSlotRecommendRequestDto;
import com.mapzip.recommend.dto.SlotInfoDto;
import com.mapzip.recommend.entity.RecommendationSelectionEntity;
import com.mapzip.recommend.repository.RecommendationSelectionRepository;
import com.mapzip.recommend.service.RecommendRequestService;

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


	@Override
	public void sendRecommendRequest(RecommendRequest request, StreamObserver<RecommendResponse> responseObserver) {
		// 추천 처리 로직 호출
		recommendRequestService.sendRecommendRequest(request.getScheduleId());

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
		 selectionRepository.deleteByUserId(request.getUserId());
		
		// 요청에서 유저 및 스케줄 정보 추출
		String userId = request.getUserId();
		String scheduleId = request.getScheduleId();
		List<SelectedPlace> selectedPlaces = request.getSelectedPlacesList();
		for (SelectedPlace place : request.getSelectedPlacesList()) {
			log.info("Saving place: slotId={}, id={}, name={}", 
			        place.getSlotId(), place.getId(), place.getPlaceName());

			RecommendationSelectionEntity entity = RecommendationSelectionEntity.builder().userId(userId)
					.scheduleId(scheduleId).slotId(place.getSlotId()).placeId(place.getId())
					.placeName(place.getPlaceName()).mealType(place.getMealType())
					.scheduledTime(place.getScheduledTime()).reason(place.getReason()).distance(place.getDistance())
					.addressName(place.getAddressName())
					.placeUrl(place.getPlaceUrl())
					.selectedDate(LocalDate.now())
					.averageRating(place.getAverageRating())
					.representativeReview(place.getRepresentativeReview())
					.build();
			selectionRepository.save(entity);
		}

		SubmitResponse response = SubmitResponse.newBuilder().setStatus("OK").setMessage("✅ 선택된 식당들이 성공적으로 저장되었습니다.").build();

		responseObserver.onNext(response);
		responseObserver.onCompleted();
	}
	
	@Override
	public void getRecommendationResults(GetRecommendationResultsRequest request,
	                                     StreamObserver<GetRecommendationResultsResponse> responseObserver) {

	    final String userId = request.getUserId();
	    final String scheduleId = request.getScheduleId();

	    // 키 포맷: recommend:{userId}:{scheduleId}:{slotId}:{MEAL|SNACK}:{placeN}
	    final String redisKeyPattern = String.format("recommend:%s:%s:*:*:place*", userId, scheduleId);

	    Set<String> keys = redisTemplate.keys(redisKeyPattern);

	    if (keys == null || keys.isEmpty()) {
	        GetRecommendationResultsResponse response = GetRecommendationResultsResponse.newBuilder()
	                .setStatus("PENDING")
	                .setMessage("추천 결과가 아직 준비되지 않았습니다.")
	                .build();
	        responseObserver.onNext(response);
	        responseObserver.onCompleted();
	        return;
	    }

	    // 정렬을 위해 order를 같이 보관
	    class PlaceWithOrder {
	        final PlaceInfo place;
	        final int order;
	        PlaceWithOrder(PlaceInfo p, int o) { this.place = p; this.order = o; }
	    }

	    Map<String, List<PlaceWithOrder>> slotMap = new HashMap<>();
	    ObjectMapper objectMapper = new ObjectMapper();

	    // 키를 한 번 정렬(슬롯/플레이스 순으로 보장하고 싶으면 comparator 조정)
	    List<String> sortedKeys = new ArrayList<>(keys);
	    Collections.sort(sortedKeys);

	    for (String key : sortedKeys) {
	        String value = redisTemplate.opsForValue().get(key);
	        if (value == null) continue;

	        try {
	            // 키 파싱
	            String[] parts = key.split(":");
	            // expect: [recommend, userId, scheduleId, slot1, MEAL, place3]
	            if (parts.length < 6) {
	                log.warn("키 형식 불일치: {}", key);
	                continue;
	            }
	            String slotId = parts[3];
	            String mealTypeToken = parts[4];              // MEAL | SNACK
	            String placeToken   = parts[5];               // place3
	            int placeOrder = 0;
	            try {
	                placeOrder = Integer.parseInt(placeToken.replaceFirst("place", ""));
	            } catch (NumberFormatException ignore) { /* 0으로 두기 */ }

	            int mealTypeFromKey =
	                    "MEAL".equalsIgnoreCase(mealTypeToken)  ? 0 :
	                    "SNACK".equalsIgnoreCase(mealTypeToken) ? 1 : -1;

	            // 값(JSON) 파싱
	            JsonNode node = objectMapper.readTree(value);

	            int mealType = node.path("mealType").isMissingNode()
	                    ? mealTypeFromKey
	                    : node.path("mealType").asInt(mealTypeFromKey);

	            PlaceInfo place = PlaceInfo.newBuilder()
	                    .setId(node.path("id").asText(""))
	                    .setPlaceName(node.path("placeName").asText(""))
	                    .setReason(node.path("reason").asText(""))
	                    .setDistance(node.path("distance").asText(""))
	                    .setScheduledTime(node.path("scheduledTime").asText("")) // 없으면 빈 문자열
	                    .setMealType(mealType >= 0 ? mealType : 0)              // 기본 0(식사)
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

	    // 슬롯별 placeN 순으로 정렬해서 proto로 변환
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

	    GetRecommendationResultsResponse response = GetRecommendationResultsResponse.newBuilder()
	            .addAllSlotRecommendations(slotRecommendations)
	            .setStatus("OK")
	            .setMessage("추천 결과를 성공적으로 불러왔습니다.")
	            .build();

	    responseObserver.onNext(response);
	    responseObserver.onCompleted();
	}

	
	@Override
	public void getSelectedPlaces(GetSelectedPlacesRequest request,
	                              StreamObserver<GetSelectedPlacesResponse> responseObserver) {
	    String userId = request.getUserId();
	    String scheduleId = request.getScheduleId();

	    // DB에서 유저 및 스케줄 기준으로 데이터 조회
	    List<RecommendationSelectionEntity> selectedList =
	            selectionRepository.findByUserIdAndScheduleId(userId, scheduleId);

	    if (selectedList == null || selectedList.isEmpty()) {
	        GetSelectedPlacesResponse response = GetSelectedPlacesResponse.newBuilder()
	                .setStatus("NOT_FOUND")
	                .setMessage("선택된 식당이 없습니다.")
	                .build();

	        responseObserver.onNext(response);
	        responseObserver.onCompleted();
	        return;
	    }

	    Map<String, List<PlaceInfo>> slotMap = new HashMap<>();

	    for (RecommendationSelectionEntity entity : selectedList) {
	        PlaceInfo place = PlaceInfo.newBuilder()
	                .setId(entity.getPlaceId())
	                .setPlaceName(entity.getPlaceName())
	                .setReason(entity.getReason())
	                .setDistance(entity.getDistance())
	                .setScheduledTime(entity.getScheduledTime())
	                .setMealType(entity.getMealType())
	                .setPlaceUrl(entity.getPlaceUrl())
	                .setAddressName(entity.getAddressName())
	                .setAverageRating(entity.getAverageRating())
	                .setRepresentativeReview(entity.getRepresentativeReview())
	                .build();

	        slotMap.computeIfAbsent(entity.getSlotId(), k -> new ArrayList<>()).add(place);
	    }

	    // SlotRecommendation 리스트로 변환
	    List<SlotSelectedPlaces> slotPlaces = slotMap.entrySet().stream()
	    	    .map(entry -> SlotSelectedPlaces.newBuilder()
	    	        .setSlotId(entry.getKey())
	    	        .addAllPlaces(entry.getValue())
	    	        .build())
	    	    .collect(Collectors.toList());


	    // 최종 응답 생성
	    GetSelectedPlacesResponse response = GetSelectedPlacesResponse.newBuilder()
	    	    .addAllSlotPlaces(slotPlaces) 
	    	    .setStatus("OK")
	    	    .setMessage("성공적으로 선택된 식당을 불러왔습니다.")
	    	    .build();


	    responseObserver.onNext(response);
	    responseObserver.onCompleted();
	}
	
	//to 리뷰 서버
	@Override
	public void placesForReview(PlacesForReviewRequest request,
	                            StreamObserver<PlacesForReviewResponse> responseObserver) {

	    //0.userid 가져오
	    String userId = GrpcHeaderConfig.UserIdContext.USER_ID.get();

	    // 1. 유저의 추천 선택 식당 조회
	    List<RecommendationSelectionEntity> selectedList = selectionRepository.findByUserId(userId);

	    if (selectedList == null || selectedList.isEmpty()) {
	        PlacesForReviewResponse response = PlacesForReviewResponse.newBuilder()
	                .setStatus("NOT_FOUND")
	                .setMessage("리뷰를 위한 식당 선택 정보가 없습니다.")
	                .build();
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
	        ReviewPlaceInfo reviewPlaceInfo = ReviewPlaceInfo.newBuilder()
	                .setId(placeId)
	                .setAddressName(entity.getAddressName())
	                .setPlaceUrl(entity.getPlaceUrl())
	                .setScheduledTime(entity.getSelectedDate().format(formatter))
	                .build();

	        responsePlaces.add(reviewPlaceInfo);

	        // 응답 보냈다고 Redis에 기록
	        redisTemplate.opsForValue().set(redisKey, "true", Duration.ofHours(24));
	    }

	    // 3. 응답 생성
	    PlacesForReviewResponse response = PlacesForReviewResponse.newBuilder()
	            .addAllPlaces(responsePlaces)
	            .setStatus("OK")
	            .setMessage("리뷰 서버로 응답이 성공적으로 전달되었습니다.")
	            .build();

	    responseObserver.onNext(response);
	    responseObserver.onCompleted();
	}


	

}
