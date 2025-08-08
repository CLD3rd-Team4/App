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

@RestController
@RequestMapping("/oauth2")
@RequiredArgsConstructor
public class UserController {

    private final KakaoOAuthService kakaoOAuthService;

    @GetMapping("/me/kakaoid")
    public ResponseEntity<String> getKakaoId(
            @AuthenticationPrincipal(expression = "principal") String kakaoId
    ) {
        return ResponseEntity.ok("내 카카오 ID: " + kakaoId);
    }

    // Refresh Token을 통한 Access Token 재발급
    @PostMapping("/token/refresh")
    public ResponseEntity<TokenResponseDto> refresh(@RequestBody RefreshTokenRequestDto request) {
        TokenResponseDto token = kakaoOAuthService.reissueAccessToken(request.getRefreshToken());
        return ResponseEntity.ok(token);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue("refreshToken") String refreshToken) {
        System.out.println("로그아웃 요청");
        kakaoOAuthService.logout(refreshToken); // Redis에서 삭제
        // 쿠키 만료시킴
        ResponseCookie expiredAccessToken = ResponseCookie.from("accessToken", "")
                .httpOnly(true).secure(false).path("/").maxAge(0).sameSite("None").build();

        ResponseCookie expiredRefreshToken = ResponseCookie.from("refreshToken", "")
                .httpOnly(true).secure(false).path("/").maxAge(0).sameSite("None").build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredAccessToken.toString())
                .header(HttpHeaders.SET_COOKIE, expiredRefreshToken.toString())
                .build();
    }


}