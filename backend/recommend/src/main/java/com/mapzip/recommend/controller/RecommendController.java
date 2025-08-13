package com.mapzip.recommend.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.mapzip.recommend.dto.tmap.TmapScheduleRequest;
import com.mapzip.recommend.service.RouteService;
import com.mapzip.recommend.service.TmapRouteCalculator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@Slf4j
public class RecommendController {

    private final TmapRouteCalculator tmapRouteCalculator;

    @PostMapping("/api/tmap")
    public ResponseEntity<Map<String, Object>> calculateRoute(@RequestBody TmapScheduleRequest request) {
        Map<String, Object> result = tmapRouteCalculator.calculate(request);
        return ResponseEntity.ok(result);
    }
}
