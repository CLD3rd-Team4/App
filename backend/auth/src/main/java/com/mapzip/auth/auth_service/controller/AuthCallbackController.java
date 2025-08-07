package com.mapzip.auth.auth_service.controller;

import com.mapzip.auth.auth_service.dto.TokenResponseDto;
import com.mapzip.auth.auth_service.service.KakaoOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthCallbackController {

    private final KakaoOAuthService kakaoOAuthService;

    @GetMapping("/kakao/callback")
    public ResponseEntity<Map<String, String>> handleKakaoCallback(@RequestParam String code) {
        System.out.println("callback 도착");
        TokenResponseDto token = kakaoOAuthService.loginWithKakao(code);
        System.out.println("controller에 token 도착");
        // 쿠키 설정
        ResponseCookie accessTokenCookie = ResponseCookie.from("accessToken", token.getAccessToken())
                .httpOnly(true)
                .secure(false) // HTTPS 환경일 경우 true
                .path("/")
                .maxAge(Duration.ofHours(1))
                .sameSite("None") // 또는 Strict, None (CORS 정책에 따라)
                .build();

        // refreshToken 쿠키 설정
        ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", token.getRefreshToken())
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(Duration.ofDays(1))
                .sameSite("None")
                .build();

        System.out.println("쿠키 설정");
        Map<String, String> response = new HashMap<>();
        response.put("message", "로그인 성공");

        return ResponseEntity.ok()
                .headers(headers -> {
                    headers.add(HttpHeaders.SET_COOKIE, accessTokenCookie.toString());
                    headers.add(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());
                })
                .body(response);
    }
}

