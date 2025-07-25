package com.mapzip.schedule.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.mapzip.schedule.dto.Feature;
import com.mapzip.schedule.dto.TmapRouteResponse;
import com.mapzip.schedule.util.TimeUtil;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Service
public class RouteService {

    public List<CalculatedLocation> calculateMealLocations(
            TmapRouteResponse tmapResponse,
            List<com.mapzip.schedule.grpc.MealTimeSlot> mealSlots,
            LocalDateTime departureDateTime
    ) {
        // Point Feature에서 totalTime 추출
        int totalTimeInSeconds = tmapResponse.getFeatures().stream()
                .filter(f -> "Point".equalsIgnoreCase(f.getGeometry().getType()) && "S".equals(f.getProperties().getPointType()))
                .findFirst()
                .map(f -> f.getProperties().getTotalTime())
                .orElseThrow(() -> new IllegalArgumentException("No start point feature in Tmap response"));

        // LineString Feature에서 coordinates 추출
        Feature routeFeature = tmapResponse.getFeatures().stream()
                .filter(f -> "LineString".equalsIgnoreCase(f.getGeometry().getType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No LineString feature in Tmap response"));

        List<List<Double>> coordinates;
        Object rawCoordinates = routeFeature.getGeometry().getCoordinates();
        if (rawCoordinates instanceof List) {
            try {
                coordinates = ((List<?>) rawCoordinates).stream()
                        .map(item -> (List<Double>) item)
                        .collect(Collectors.toList());
            } catch (ClassCastException e) {
                throw new IllegalArgumentException("Invalid coordinate format in LineString", e);
            }
        } else {
            throw new IllegalArgumentException("Coordinates is not a List");
        }

        List<CalculatedLocation> calculatedLocations = new ArrayList<>();

        for (var mealSlot : mealSlots) {
            com.mapzip.schedule.grpc.MealTimeSlot grpcMealSlot = (com.mapzip.schedule.grpc.MealTimeSlot) mealSlot;
            LocalDateTime mealDateTime = TimeUtil.parseKoreanAmPmToFuture(grpcMealSlot.getScheduledTime());
            // 식사 시간이 출발 시간보다 과거이면 다음날로 처리
            if(mealDateTime.isBefore(departureDateTime)){
                mealDateTime = mealDateTime.plusDays(1);
            }

            long secondsFromDeparture = ChronoUnit.SECONDS.between(departureDateTime, mealDateTime);

            List<Double> location;
            if (secondsFromDeparture < 0) {
                location = coordinates.get(0);
            } else if (secondsFromDeparture >= totalTimeInSeconds) {
                location = coordinates.get(coordinates.size() - 1);
            } else {
                double timeRatio = (double) secondsFromDeparture / totalTimeInSeconds;
                int targetIndex = (int) ((coordinates.size() - 1) * timeRatio);
                location = coordinates.get(targetIndex);
            }
            calculatedLocations.add(new CalculatedLocation(grpcMealSlot.getSlotId(), location.get(1), location.get(0)));
        }
        return calculatedLocations;
    }

    @Getter
    @AllArgsConstructor
    public static class CalculatedLocation {
        private String slotId;
        private double lat;
        private double lon;
    }
}
