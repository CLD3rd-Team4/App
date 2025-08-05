package com.mapzip.schedule.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapzip.schedule.dto.Feature;
import com.mapzip.schedule.dto.TmapRouteResponse;
import com.mapzip.schedule.util.TimeUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
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
        log.info("Starting meal location calculation for {} meal slots.", mealSlots.size());
        List<TimePoint> timePoints = createTimePointsFromResponse(tmapResponse);

        List<CalculatedLocation> calculatedLocations = new ArrayList<>();
        for (var mealSlot : mealSlots) {
            try {
                LocalDateTime mealDateTime = TimeUtil.parseKoreanAmPmToFuture(mealSlot.getScheduledTime(), departureDateTime.toLocalDate());
                if (mealDateTime.isBefore(departureDateTime)) {
                    mealDateTime = mealDateTime.plusDays(1);
                }
                long secondsFromDeparture = ChronoUnit.SECONDS.between(departureDateTime, mealDateTime);
                log.info("Slot '{}' is {} seconds from departure.", mealSlot.getScheduledTime(), secondsFromDeparture);

                if (secondsFromDeparture < 0) {
                    if (!timePoints.isEmpty()) {
                        Coordinate departureCoord = timePoints.get(0).getCoordinate();
                        calculatedLocations.add(new CalculatedLocation(mealSlot.getId(), departureCoord.getLat(), departureCoord.getLon()));
                    }
                    continue;
                }

                TimePoint closestPoint = findClosestTimePoint(timePoints, secondsFromDeparture);
                Coordinate location = closestPoint.getCoordinate();
                log.info("Found closest point for slot '{}' at time {}s -> Lat: {}, Lon: {}", mealSlot.getScheduledTime(), closestPoint.getTime(), location.getLat(), location.getLon());
                calculatedLocations.add(new CalculatedLocation(mealSlot.getId(), location.getLat(), location.getLon()));
            } catch (Exception e) {
                log.error("Error calculating location for meal slot: {}", mealSlot.getId(), e);
            }
        }
        log.info("Successfully calculated {} locations.", calculatedLocations.size());
        return calculatedLocations;
    }

    private List<TimePoint> createTimePointsFromResponse(TmapRouteResponse tmapResponse) {
        List<TimePoint> timePoints = new ArrayList<>();
        long accumulatedTime = 0;

        for (Feature feature : tmapResponse.getFeatures()) {
            if ("Point".equalsIgnoreCase(feature.getGeometry().getType())) {
                Coordinate currentCoord = parseCoordinates(feature.getGeometry().getCoordinates()).get(0);
                long timeToPoint = feature.getProperties().getTotalTime() != null ? feature.getProperties().getTotalTime() : accumulatedTime;
                timePoints.add(new TimePoint(timeToPoint, currentCoord));
            } else if ("LineString".equalsIgnoreCase(feature.getGeometry().getType())) {
                if (feature.getProperties().getTime() != null) {
                    accumulatedTime += feature.getProperties().getTime();
                }
            }
        }
        log.info("Created {} time points for route calculation.", timePoints.size());
        return timePoints;
    }

    private TimePoint findClosestTimePoint(List<TimePoint> timePoints, long targetTime) {
        if (timePoints.isEmpty()) throw new IllegalStateException("No time points available.");
        return timePoints.stream()
                .min((p1, p2) -> Long.compare(Math.abs(p1.getTime() - targetTime), Math.abs(p2.getTime() - targetTime)))
                .orElse(timePoints.get(0));
    }

    private List<Coordinate> parseCoordinates(Object rawCoordinates) {
        try {
            if (rawCoordinates instanceof List) {
                List<?> list = (List<?>) rawCoordinates;
                if (!list.isEmpty() && list.get(0) instanceof List) {
                    List<List<Double>> parsedList = objectMapper.convertValue(rawCoordinates, new TypeReference<>() {});
                    return parsedList.stream().map(p -> new Coordinate(p.get(1), p.get(0))).collect(Collectors.toList());
                } else if (!list.isEmpty() && list.get(0) instanceof Double) {
                    List<Double> point = objectMapper.convertValue(rawCoordinates, new TypeReference<>() {});
                    return List.of(new Coordinate(point.get(1), point.get(0)));
                }
            }
            throw new IllegalArgumentException("Unsupported coordinate format: " + rawCoordinates);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid coordinate format: " + rawCoordinates, e);
        }
    }

    @Getter
    @AllArgsConstructor
    private static class TimePoint {
        private long time;
        private Coordinate coordinate;
    }

    @Getter
    @AllArgsConstructor
    private static class Coordinate {
        private double lat;
        private double lon;
    }

    @Getter
    @AllArgsConstructor
    public static class CalculatedLocation {
        private String slotId;
        private double lat;
        private double lon;
    }
}