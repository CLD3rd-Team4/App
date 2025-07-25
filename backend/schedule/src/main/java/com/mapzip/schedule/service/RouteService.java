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
            List<com.mapzip.schedule.entity.MealTimeSlot> mealSlots,
            LocalDateTime departureDateTime
    ) {
        // Point Feature에서 totalTime 및 totalDistance 추출
        Feature startPointFeature = tmapResponse.getFeatures().stream()
                .filter(f -> "Point".equalsIgnoreCase(f.getGeometry().getType()) && "S".equals(f.getProperties().getPointType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No start point feature in Tmap response"));

        int totalTimeInSeconds = startPointFeature.getProperties().getTotalTime();

        // LineString Feature에서 coordinates 추출
        Feature routeFeature = tmapResponse.getFeatures().stream()
                .filter(f -> "LineString".equalsIgnoreCase(f.getGeometry().getType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No LineString feature in Tmap response"));

        List<Coordinate> coordinates = parseCoordinates(routeFeature.getGeometry().getCoordinates());
        if (coordinates.size() < 2) {
            throw new IllegalStateException("Route requires at least two coordinates.");
        }


        // 각 좌표 구간별 거리 계산 및 총 거리 합산
        List<Double> segmentDistances = new ArrayList<>();
        double totalDistance = 0;
        for (int i = 0; i < coordinates.size() - 1; i++) {
            double distance = coordinates.get(i).distanceTo(coordinates.get(i + 1));
            segmentDistances.add(distance);
            totalDistance += distance;
        }

        if (totalTimeInSeconds <= 0) {
            throw new IllegalStateException("Total time from Tmap API is zero or negative, cannot calculate route.");
        }
        
        double averageSpeed = totalDistance / totalTimeInSeconds;
        List<CalculatedLocation> calculatedLocations = new ArrayList<>();

        for (var mealSlot : mealSlots) {
            LocalDateTime mealDateTime = TimeUtil.parseKoreanAmPmToFuture(mealSlot.getScheduledTime(), departureDateTime.toLocalDate());

            // 식사 시간이 출발 시간보다 과거이면 다음 날로 처리
            if (mealDateTime.isBefore(departureDateTime)) {
                mealDateTime = mealDateTime.plusDays(1);
            }

            long secondsFromDeparture = ChronoUnit.SECONDS.between(departureDateTime, mealDateTime);

            Coordinate location;
            if (secondsFromDeparture < 0) {
                location = coordinates.get(0);
            } else if (secondsFromDeparture >= totalTimeInSeconds) {
                location = coordinates.get(coordinates.size() - 1);
            } else {
                double targetDistance = averageSpeed * secondsFromDeparture;
                double accumulatedDistance = 0;
                location = coordinates.get(coordinates.size() - 1); // 기본값: 목적지

                for (int i = 0; i < segmentDistances.size(); i++) {
                    double segmentDistance = segmentDistances.get(i);
                    if (accumulatedDistance + segmentDistance >= targetDistance) {
                        double ratio = (targetDistance - accumulatedDistance) / segmentDistance;
                        location = Coordinate.interpolate(coordinates.get(i), coordinates.get(i + 1), ratio);
                        break;
                    }
                    accumulatedDistance += segmentDistance;
                }
            }
            calculatedLocations.add(new CalculatedLocation(mealSlot.getId(), location.getLat(), location.getLon()));
        }
        return calculatedLocations;
    }

    private List<Coordinate> parseCoordinates(Object rawCoordinates) {
        if (!(rawCoordinates instanceof List)) {
            throw new IllegalArgumentException("Coordinates is not a List");
        }
        try {
            return ((List<?>) rawCoordinates).stream()
                    .map(item -> {
                        List<Double> point = (List<Double>) item;
                        return new Coordinate(point.get(1), point.get(0)); // Tmap: lon, lat -> 우리: lat, lon
                    })
                    .collect(Collectors.toList());
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Invalid coordinate format in LineString", e);
        }
    }

    @Getter
    @AllArgsConstructor
    private static class Coordinate {
        private double lat;
        private double lon;

        public double distanceTo(Coordinate other) {
            final int R = 6371000; // 지구 반지름 (미터)
            double latDistance = Math.toRadians(other.lat - this.lat);
            double lonDistance = Math.toRadians(other.lon - this.lon);
            double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                    + Math.cos(Math.toRadians(this.lat)) * Math.cos(Math.toRadians(other.lat))
                    * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            return R * c;
        }

        public static Coordinate interpolate(Coordinate p1, Coordinate p2, double ratio) {
            double lat = p1.lat + (p2.lat - p1.lat) * ratio;
            double lon = p1.lon + (p2.lon - p1.lon) * ratio;
            return new Coordinate(lat, lon);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class CalculatedLocation {
        private String slotId;
        private double lat;
        private double lon;
    }
}
