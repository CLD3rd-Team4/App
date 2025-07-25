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

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RouteService {

    private final ObjectMapper objectMapper;

    public RouteService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<CalculatedLocation> calculateMealLocations(
            TmapRouteResponse tmapResponse,
            List<com.mapzip.schedule.entity.MealTimeSlot> mealSlots,
            LocalDateTime departureDateTime
    ) {
        List<Feature> features = tmapResponse.getFeatures();

        // 경로의 모든 Point Feature와 해당 지점까지의 누적 시간을 매핑
        List<TimePoint> timePoints = new ArrayList<>();
        long accumulatedTime = 0;
        Coordinate lastCoordinate = null;

        for (Feature feature : features) {
            if ("Point".equalsIgnoreCase(feature.getGeometry().getType())) {
                Coordinate currentCoord = parseCoordinates(feature.getGeometry().getCoordinates()).get(0);
                timePoints.add(new TimePoint(accumulatedTime, currentCoord));
                lastCoordinate = currentCoord;
            } else if ("LineString".equalsIgnoreCase(feature.getGeometry().getType())) {
                // LineString Feature일 때만 time 필드를 사용
                if (feature.getProperties().getTime() != null) {
                    accumulatedTime += feature.getProperties().getTime();
                }
            }
        }

        // 마지막 지점 (도착지) 추가
        if (lastCoordinate != null && (timePoints.isEmpty() || timePoints.get(timePoints.size() - 1).time < accumulatedTime)) {
            timePoints.add(new TimePoint(accumulatedTime, lastCoordinate));
        }

        List<CalculatedLocation> calculatedLocations = new ArrayList<>();

        for (var mealSlot : mealSlots) {
            LocalDateTime mealDateTime = TimeUtil.parseKoreanAmPmToFuture(mealSlot.getScheduledTime(), departureDateTime.toLocalDate());
            if (mealDateTime.isBefore(departureDateTime)) {
                mealDateTime = mealDateTime.plusDays(1);
            }

            long secondsFromDeparture = ChronoUnit.SECONDS.between(departureDateTime, mealDateTime);

            // 목표 시간과 가장 가까운 안내점 찾기
            TimePoint closestPoint = findClosestTimePoint(timePoints, secondsFromDeparture);
            Coordinate location = closestPoint.getCoordinate();

            calculatedLocations.add(new CalculatedLocation(mealSlot.getId(), location.getLat(), location.getLon()));
        }

        return calculatedLocations;
    }

    private TimePoint findClosestTimePoint(List<TimePoint> timePoints, long targetTime) {
        if (timePoints.isEmpty()) {
            throw new IllegalStateException("No time points available to find closest location.");
        }

        TimePoint closest = timePoints.get(0);
        long minDiff = Long.MAX_VALUE;

        for (TimePoint point : timePoints) {
            long diff = Math.abs(point.getTime() - targetTime);
            if (diff < minDiff) {
                minDiff = diff;
                closest = point;
            }
        }
        return closest;
    }

    // TimePoint 내부 클래스 추가
    @Getter
    @AllArgsConstructor
    private static class TimePoint {
        private long time; // 초 단위 누적 시간
        private Coordinate coordinate;
    }

    private List<Coordinate> parseCoordinates(Object rawCoordinates) {
        if (rawCoordinates == null) {
            return new ArrayList<>();
        }
        try {
            if (rawCoordinates instanceof List && !((List<?>) rawCoordinates).isEmpty() && ((List<?>) rawCoordinates).get(0) instanceof List) {
                // LineString (중첩 리스트) 형태: [[lon, lat], [lon, lat], ...]
                List<List<Double>> parsedList = objectMapper.convertValue(rawCoordinates, new com.fasterxml.jackson.core.type.TypeReference<List<List<Double>>>() {});
                return parsedList.stream()
                        .map(point -> new Coordinate(point.get(1), point.get(0))) // Tmap: lon, lat -> 우리: lat, lon
                        .collect(Collectors.toList());
            } else if (rawCoordinates instanceof List && !((List<?>) rawCoordinates).isEmpty() && ((List<?>) rawCoordinates).get(0) instanceof Double) {
                // Point (단일 리스트) 형태: [lon, lat]
                List<Double> point = objectMapper.convertValue(rawCoordinates, new com.fasterxml.jackson.core.type.TypeReference<List<Double>>() {});
                return List.of(new Coordinate(point.get(1), point.get(0))); // Tmap: lon, lat -> 우리: lat, lon
            } else {
                throw new IllegalArgumentException("Unsupported coordinate format: " + rawCoordinates);
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid coordinate format in LineString: " + rawCoordinates, e);
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