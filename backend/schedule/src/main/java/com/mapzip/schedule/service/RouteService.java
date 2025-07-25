package com.mapzip.schedule.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

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
        Feature routeFeature = tmapResponse.getFeatures().stream()
                .filter(f -> "LineString".equalsIgnoreCase(f.getGeometry().getType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No LineString feature in Tmap response"));

        List<List<Double>> coordinates = (List<List<Double>>) (List<?>) routeFeature.getGeometry().getCoordinates();
        int totalTimeInSeconds = routeFeature.getProperties().getTotalTime();

        List<CalculatedLocation> calculatedLocations = new ArrayList<>();

        for (var mealSlot : mealSlots) {
            LocalDateTime mealDateTime = TimeUtil.parseKoreanAmPmToFuture(mealSlot.getScheduledTime());
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
            calculatedLocations.add(new CalculatedLocation(mealSlot.getSlotId(), location.get(1), location.get(0)));
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
