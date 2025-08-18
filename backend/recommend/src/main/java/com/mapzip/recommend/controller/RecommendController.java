//package com.mapzip.recommend.controller;
//
//import java.util.Map;
//
//import org.springframework.http.MediaType;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//
//import com.google.protobuf.util.JsonFormat;
//import com.mapzip.recommend.dto.tmap.TmapScheduleRequest;
//import com.mapzip.recommend.grpc.RecommendResponse;
//import com.mapzip.recommend.service.RecommendRequestService;
//import com.mapzip.recommend.service.RouteService;
//import com.mapzip.recommend.service.TmapRouteCalculator;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//
//@RestController
//@RequiredArgsConstructor
//@Slf4j
//public class RecommendController {
//
//    private final TmapRouteCalculator tmapRouteCalculator;
//    private final RecommendRequestService recommendRequestService;
//
//    private final JsonFormat.Printer printer = JsonFormat.printer()
//            .includingDefaultValueFields()
//            .preservingProtoFieldNames();
//
//    @PostMapping("/api/tmap")
//    public ResponseEntity<Map<String, Object>> calculateRoute(@RequestBody TmapScheduleRequest request) {
//        Map<String, Object> result = tmapRouteCalculator.calculate(request);
//        return ResponseEntity.ok(result);
//    }
//    @PostMapping(value = "/request", produces = MediaType.APPLICATION_JSON_VALUE)
//    public ResponseEntity<String> sendRecommendRequest(
//            @RequestParam(value = "scheduleId", required = false) String scheduleId,
//            @RequestBody(required = false) Map<String, Object> body
//    ) throws Exception {
//
//        if ((scheduleId == null || scheduleId.isBlank()) && body != null) {
//            Object v = body.get("scheduleId");
//            if (v != null) scheduleId = String.valueOf(v);
//        }
//        if (scheduleId == null || scheduleId.isBlank()) {
//            String err = "{\"status\":\"ERROR\",\"message\":\"scheduleId is required\"}";
//            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(err);
//        }
//
//        try {
//            // gRPC 구현에서 하던 것과 동일하게 내부 서비스 호출 (Kafka/MSK, Bedrock 등 트리거)
//            recommendRequestService.sendRecommendRequest(scheduleId);
//
//            RecommendResponse res = RecommendResponse.newBuilder()
//                    .setStatus("OK")
//                    .setMessage("스케줄 선택이 성공적으로 처리되었습니다.")
//                    .build();
//
//            return ResponseEntity.ok()
//                    .contentType(MediaType.APPLICATION_JSON)
//                    .body(printer.print(res));
//
//        } catch (Exception e) {
//            log.error("sendRecommendRequest failed. scheduleId={}", scheduleId, e);
//            String err = "{\"status\":\"ERROR\",\"message\":\"internal error while triggering recommendation\"}";
//            return ResponseEntity.internalServerError()
//                    .contentType(MediaType.APPLICATION_JSON)
//                    .body(err);
//        }
//    }
//}
