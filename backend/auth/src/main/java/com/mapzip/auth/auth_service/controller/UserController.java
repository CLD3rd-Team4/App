package com.mapzip.auth.auth_service.controller;

import com.mapzip.auth.auth_service.dto.KakaoLoginRequestDto;
import com.mapzip.auth.auth_service.dto.RefreshTokenRequestDto;
import com.mapzip.auth.auth_service.dto.TokenResponseDto;
import com.mapzip.auth.auth_service.service.KakaoOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserController {

    private final KakaoOAuthService kakaoOAuthService;

    @PostMapping("/kakao/callback")
    public ResponseEntity<Map<String, String>> handleKakaoCallback(@RequestParam String code) {
        System.out.println("callback 요청");
        TokenResponseDto token = kakaoOAuthService.loginWithKakao(code);

        // 쿠키 설정
        ResponseCookie accessTokenCookie = ResponseCookie.from("accessToken", token.getAccessToken())
                .httpOnly(true)
                .secure(true)
                .domain(".mapzip.shop")
                .path("/")
                .maxAge(Duration.ofDays(1))
                .sameSite("None")
                .build();

        // refreshToken 쿠키 설정
        ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", token.getRefreshToken())
                .httpOnly(true)
                .secure(true)
                .domain(".mapzip.shop")
                .path("/auth/token")
                .maxAge(Duration.ofDays(1))
                .sameSite("None")
                .build();

        System.out.println("쿠키 설정 완료");
        Map<String, String> response = new HashMap<>();
        response.put("message", "로그인 성공");

        return ResponseEntity.ok()
                .headers(headers -> {
                    headers.add(HttpHeaders.SET_COOKIE, accessTokenCookie.toString());
                    headers.add(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());
                })
                .body(response);
    }

    @GetMapping("/me/kakaoid")
    public ResponseEntity<String> getKakaoId(
            @AuthenticationPrincipal(expression = "principal") String kakaoId
    ) {
        return ResponseEntity.ok("내 카카오 ID: " + kakaoId);
    }

    // Refresh Token을 통한 Access Token 재발급
    @PostMapping("/token/refresh")
    public ResponseEntity<Map<String, String>> refresh(@CookieValue("refreshToken") String refreshToken) {
        TokenResponseDto token = kakaoOAuthService.reissueAccessToken(refreshToken);

        ResponseCookie access = ResponseCookie.from("accessToken", token.getAccessToken())
                .httpOnly(true)
                .secure(true)
                .domain(".mapzip.shop")
                .path("/")
                .maxAge(Duration.ofHours(1))
                .sameSite("None")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, access.toString())
                .body(Map.of("message", "토큰 재발급 완료"));
    }

    @PostMapping("/token/logout")
    public ResponseEntity<Void> logout(@CookieValue("refreshToken") String refreshToken) {
        System.out.println("로그아웃 요청");

        kakaoOAuthService.logout(refreshToken); // Redis에서 삭제

        // access 쿠키 만료
        ResponseCookie expiredAccessToken = ResponseCookie.from("accessToken", "")
                .httpOnly(true)
                .secure(true)
                .domain(".mapzip.shop")
                .path("/")
                .sameSite("None")
                .maxAge(0)
                .build();

        // refresh 쿠키 만료
        ResponseCookie expiredRefreshToken = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .domain(".mapzip.shop")
                .path("/auth/token")
                .sameSite("None")
                .maxAge(0)
                .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredAccessToken.toString())
                .header(HttpHeaders.SET_COOKIE, expiredRefreshToken.toString())
                .build();
    }
}