package com.mapzip.recommend.mapper;

import java.util.List;
import java.util.stream.Collectors;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.mapzip.recommend.dto.tmap.MealSlotData;
import com.mapzip.recommend.dto.tmap.TmapScheduleRequest;
import com.mapzip.recommend.dto.tmap.TmapScheduleRequest.LocationDto;
import com.mapzip.recommend.dto.tmap.TmapScheduleRequest.RecommendUpdateContext;
import com.mapzip.schedule.grpc.GetScheduleDetailResponse;
import com.mapzip.schedule.grpc.MealTimeSlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;


public class TmapRequestMapper {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	public static TmapScheduleRequest fromScheduleDetail(GetScheduleDetailResponse.ScheduleDetail detail,
			String scheduleId, String userId, String clientNowIso, // 프론트 전달값
			Double currentLat, Double currentLng, boolean IsUpdate, String runId) {

		TmapScheduleRequest request = new TmapScheduleRequest();
		request.setScheduleId(scheduleId);
		request.setUserId(userId);
		String KoreanDepartureTime = toKoreanAmPm(detail.getDepartureTime());
		request.setDepartureTime(KoreanDepartureTime);
		request.setRunId(runId);

		// 출발지
		LocationDto departure = convertLocation(detail.getDeparture().getLat(), detail.getDeparture().getLng(),
				detail.getDeparture().getName());
		request.setDeparture(departure);

		// 도착지
		LocationDto destination = convertLocation(detail.getDestination().getLat(), detail.getDestination().getLng(),
				detail.getDestination().getName());
		request.setDestination(destination);

		// 경유지
		List<LocationDto> waypoints = detail.getWaypointsList().stream()
				.map(wp -> convertLocation(wp.getLat(), wp.getLng(), wp.getName())).collect(Collectors.toList());
		request.setWaypoints(waypoints);

		// 식사 슬롯
		final Logger log = LoggerFactory.getLogger(TmapRequestMapper.class);

		// 1) 슬롯 원본을 먼저 로깅 (사람이 보기 좋은 형태)
		if (log.isInfoEnabled()) {
			log.info("[MEAL-SLOTS] scheduleId={} total={}", scheduleId, detail.getMealSlotsList().size());

			detail.getMealSlotsList().forEach(slot -> {
				final String time = toKoreanAmPm(slot.getScheduledTime());
				final int typeNum = slot.getMealType() != null ? slot.getMealType().getNumber() : -1;
				final String typeName = slot.getMealType() != null ? slot.getMealType().name() : "NULL";

				log.info("[MEAL-SLOTS] scheduleId={} slotId={} time={} mealType={}({}) radius={}", scheduleId,
						slot.getSlotId(), time, typeNum, typeName, slot.getRadius());
			});
		}

		// 2) 매핑
		List<MealSlotData> mealSlots = detail.getMealSlotsList().stream()
				.map(slot -> MealSlotData.builder().slotId(slot.getSlotId())
						.scheduledTime(toKoreanAmPm(slot.getScheduledTime())).radius(slot.getRadius())
						.mealType(slot.getMealType() != null ? slot.getMealType().getNumber() : -1).build())
				.collect(Collectors.toList());

		request.setMealSlots(mealSlots);

		// 3) 매핑 결과도 필요하면 한 번 더 요약 로그
		if (log.isDebugEnabled()) {
			mealSlots.forEach(
					ms -> log.info("[MEAL-SLOTS/MAPPED] scheduleId={} slotId={} time={} mealType={} radius={}",
							scheduleId, ms.getSlotId(), ms.getScheduledTime(), ms.getMealType(), ms.getRadius()));
		}

		// 기타
		request.setUserNote(detail.getUserNote());
		request.setPurpose(detail.getPurpose());
		request.setCompanions(detail.getCompanionsList());

		// 추천 업데이트 컨텍스트
		RecommendUpdateContext ctx = new RecommendUpdateContext();
		ctx.setIsUpdate(IsUpdate);

		if (IsUpdate) {
			// clientNowIso를 "오전/오후 HH:mm" 형식(시간 두 자리)으로 변환하여 저장
			String displayTime = toKoreanAmPm(clientNowIso);
			ctx.setClientNowIso(displayTime);
			ctx.setCurrentLat(currentLat);
			ctx.setCurrentLng(currentLng);
		} else {
		}

		request.setRecommendUpdateContext(ctx);
		return request;
	}

	private static LocationDto convertLocation(double lat, double lng, String name) {
		LocationDto dto = new LocationDto();
		dto.setLat(String.valueOf(lat));
		dto.setLng(String.valueOf(lng));
		dto.setName(name);
		return dto;
	}

	private static String toKoreanAmPm(String input) {
		if (input == null || input.isBlank())
			return "";

		String s = input.trim();

		// 이미 "오전/오후 H:mm" or "오전/오후 HH:mm" 인 경우 → 두 자리 시(hour)로 보정
		java.util.regex.Matcher m1 = java.util.regex.Pattern.compile("(오전|오후)\\s*(\\d{1,2}):(\\d{2})").matcher(s);
		if (m1.matches()) {
			String period = m1.group(1);
			int hour = Integer.parseInt(m1.group(2));
			String mm = m1.group(3);
			String hh2 = String.format("%02d", hour); // 시간 두 자리
			return period + " " + hh2 + ":" + mm;
		}

		// "HH:mm" 형태면 오전/오후 판별해서 변환
		java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(s);
		if (m2.matches()) {
			int h = Integer.parseInt(m2.group(1));
			String mm = m2.group(2);
			String period = (h >= 12) ? "오후" : "오전";
			int displayHour = (h == 0) ? 12 : (h > 12 ? h - 12 : h);
			String hh2 = String.format("%02d", displayHour);
			return period + " " + hh2 + ":" + mm;
		}

		// ISO 같은 경우
		try {
			ZonedDateTime zdt = Instant.parse(s).atZone(KST);
			int h = zdt.getHour();
			int m = zdt.getMinute();
			String period = (h >= 12) ? "오후" : "오전";
			int displayHour = (h == 0) ? 12 : (h > 12 ? h - 12 : h);
			String hh2 = String.format("%02d", displayHour);
			String mm2 = String.format("%02d", m);
			return period + " " + hh2 + ":" + mm2;
		} catch (Exception ignore) {
			// "오전/오후 HH:mm" 규격과 다르면 그대로 반환
			return s;
		}
	}
}
