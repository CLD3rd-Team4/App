package com.mapzip.recommend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.mapzip.recommend.dto.RecommendRequestDto;
import com.mapzip.recommend.service.RecommendRequestService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class RecommendController {

	private final RecommendRequestService recommendRequestService;
	
	//////// 1. 스케줄 id 경로 변수 분리 2. 스케줄 선택도 추천서버에서 하면 선택까지 넣어야
	@PostMapping("/api/recommend")
	public ResponseEntity<String> sendRecommendRequest(@RequestBody RecommendRequestDto requestDto) {
		recommendRequestService.sendRecommendRequest(requestDto);
        return ResponseEntity.ok("추천 요청이 성공적으로 전송되었습니다.");
    }
}
