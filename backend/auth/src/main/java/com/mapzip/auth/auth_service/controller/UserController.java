package com.mapzip.auth.auth_service.controller;

import com.mapzip.auth.auth_service.dto.KakaoLoginRequestDto;
import com.mapzip.auth.auth_service.dto.NicknameRequestDto;
import com.mapzip.auth.auth_service.dto.RefreshTokenRequestDto;
import com.mapzip.auth.auth_service.dto.TokenResponseDto;
import com.mapzip.auth.auth_service.service.KakaoOAuthService;
import com.mapzip.auth.auth_service.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/oauth2")
@RequiredArgsConstructor
public class UserController {

    private final KakaoOAuthService kakaoOAuthService;
    private final UserService userService;

    @PostMapping("/login/kakao")
    public ResponseEntity<TokenResponseDto> kakaoLogin(@RequestBody KakaoLoginRequestDto request) {
        TokenResponseDto token = kakaoOAuthService.loginWithKakao(request.getCode());
        return ResponseEntity.ok(token);
    }

    @PatchMapping("/me/nickname")
    public ResponseEntity<String> registerNickname(
            @AuthenticationPrincipal(expression = "username") String kakaoId,
            @RequestBody NicknameRequestDto request
    ) {
        userService.registerNickname(kakaoId, request.getNickname());
        return ResponseEntity.ok("닉네임 등록 완료");
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