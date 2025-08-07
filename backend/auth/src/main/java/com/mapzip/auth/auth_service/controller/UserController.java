package com.mapzip.auth.auth_service.controller;

import com.mapzip.auth.auth_service.dto.KakaoLoginRequestDto;
import com.mapzip.auth.auth_service.dto.RefreshTokenRequestDto;
import com.mapzip.auth.auth_service.dto.TokenResponseDto;
import com.mapzip.auth.auth_service.service.KakaoOAuthService;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<Void> logout(@RequestBody RefreshTokenRequestDto request) {
        kakaoOAuthService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

}