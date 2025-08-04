package com.mapzip.recommend.grpc;

import java.util.List;

import com.mapzip.recommend.dto.MultiSlotRecommendRequestDto;
import com.mapzip.recommend.dto.SlotInfoDto;
import com.mapzip.recommend.service.RecommendRequestService;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class RecommendServiceImpl extends RecommendServiceGrpc.RecommendServiceImplBase {

	private final RecommendRequestService recommendRequestService;

	public RecommendServiceImpl(RecommendRequestService recommendRequestService) {
		this.recommendRequestService = recommendRequestService;
	}

	@Override
	public void sendRecommendRequest(RecommendRequest request, StreamObserver<RecommendResponse> responseObserver) {
		// proto → DTO 변환
		MultiSlotRecommendRequestDto dto = convertToDto(request);

		// 추천 처리 로직 호출
		recommendRequestService.sendRecommendRequest(dto);

		// 응답
		RecommendResponse response = RecommendResponse.newBuilder().setStatus("OK").setMessage("추천 요청이 성공적으로 처리되었습니다.")
				.build();

		responseObserver.onNext(response);
		responseObserver.onCompleted();
	}

	private MultiSlotRecommendRequestDto convertToDto(RecommendRequest request) {
		List<SlotInfoDto> slotDtos = request.getSlotsList().stream().map(slot -> {
			SlotInfoDto dto = new SlotInfoDto();
			dto.setSlotId(slot.getSlotId());
			dto.setLat(slot.getLat());
			dto.setLon(slot.getLon());
			dto.setMealType(slot.getMealType());
			dto.setScheduledTime(slot.getScheduledTime());
			dto.setRadius(slot.getRadius());
			return dto;
		}).toList();

		MultiSlotRecommendRequestDto dto = new MultiSlotRecommendRequestDto();
		dto.setUserId(request.getUserId());
		dto.setScheduleId(request.getScheduleId());
		dto.setRecommendationRequestIds(request.getRecommendationRequestIdsList());
		dto.setSlots(slotDtos);
		dto.setUserNote(request.getUserNote());
		dto.setPurpose(request.getPurpose());
		dto.setCompanions(request.getCompanionsList());

		return dto;
	}

}
