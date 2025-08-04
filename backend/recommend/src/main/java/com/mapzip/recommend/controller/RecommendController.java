package com.mapzip.recommend.controller;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mapzip.recommend.dto.MultiSlotRecommendRequestDto;
import com.mapzip.recommend.dto.RecommendRequestDto;
import com.mapzip.recommend.service.KakaoApiService;
import com.mapzip.recommend.service.RecommendRequestService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class RecommendController {

	private final RecommendRequestService recommendRequestService;
	private final KakaoApiService kakaoApiService;
	private final RedisTemplate<String, Object> redisTemplate;
	
	//////// 1. 스케줄 id 경로로 변수 넣어야할 거 같음 -> api 요청시
	@PostMapping("/api/recommend")
	public ResponseEntity<String> sendRecommendRequest(@RequestBody MultiSlotRecommendRequestDto multiSlotRecommendRequestDto) {
		recommendRequestService.sendRecommendRequest(multiSlotRecommendRequestDto);

        return ResponseEntity.ok("추천 요청이 성공적으로 전송되었습니다.");
    }
	
	//추천 결과 조회
	
	//추천 결과 반영 

	
}
