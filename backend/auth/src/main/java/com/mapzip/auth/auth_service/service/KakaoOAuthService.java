package com.mapzip.auth.auth_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapzip.auth.auth_service.dto.KakaoUserInfo;
import com.mapzip.auth.auth_service.dto.TokenResponseDto;
import com.mapzip.auth.auth_service.entity.AppUser;
import com.mapzip.auth.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoOAuthService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final WebClient webClient;
    private final JwtEncoder jwtEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${kakao.client-id}")
    private String clientId;

    @Value("${kakao.redirect-uri}")
    private String redirectUri;

    @Value("${kakao.client-secret:}")
    private String clientSecret;

    public TokenResponseDto loginWithKakao(String code) {
        System.out.println("loginWithKakao service 진입");
        String kakaoAccessToken = getKakaoAccessToken(code);
        KakaoUserInfo kakaoUserInfo = getKakaoUserInfo(kakaoAccessToken);

        AppUser user = userRepository.findByKakaoId(kakaoUserInfo.kakaoId())
                .orElseGet(() -> userRepository.save(AppUser.builder()
                        .kakaoId(kakaoUserInfo.kakaoId())
                        .nickname(kakaoUserInfo.nickname())
                        .build()));

        // JWT access token 생성
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(kakaoUserInfo.kakaoId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .claim("kakaoId", kakaoUserInfo.kakaoId().toString())
                .claim("nickname", kakaoUserInfo.nickname())
                .build();

        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        String refreshToken = UUID.randomUUID().toString();

        System.out.println("accessToken & refreshToken 생성");

        refreshTokenService.save(refreshToken, kakaoUserInfo.kakaoId().toString());

        return new TokenResponseDto(accessToken, refreshToken);
    }

    private String getKakaoAccessToken(String code) {
//        System.out.println("getKakaoAccessToken");
//        System.out.println("client_id: " + clientId);
//        System.out.println("redirect_uri: " + redirectUri);
//        System.out.println("code: " + code);

        String requestBody = "grant_type=authorization_code" +
                "&client_id=" + clientId +
                "&redirect_uri=" + redirectUri +
                "&code=" + code +
                "&client_secret=" + clientSecret;

        String response = webClient.post()
                .uri("https://kauth.kakao.com/oauth/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(
                        status -> status.isError(),
                        clientResponse -> clientResponse.bodyToMono(String.class).map(body -> {
                            System.out.println("카카오 응답 에러: " + body);
                            return new RuntimeException("카카오 응답 오류: " + body);
                        })
                )
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            return jsonNode.get("access_token").asText();
        } catch (Exception e) {
            throw new IllegalArgumentException("카카오 access token 파싱 실패", e);
        }
    }


    private KakaoUserInfo getKakaoUserInfo(String accessToken) {
        String response = webClient.get()
                .uri("https://kapi.kakao.com/v2/user/me")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            Long kakaoId = jsonNode.get("id").asLong();
            String nickname = jsonNode.path("properties").path("nickname").asText(null);

            return new KakaoUserInfo(kakaoId, nickname);
        } catch (Exception e) {
            throw new IllegalArgumentException("카카오 사용자 정보 파싱 실패", e);
        }
    }

    public TokenResponseDto reissueAccessToken(String refreshToken) {
        String kakaoId = refreshTokenService.getUserIdFromRefreshToken(refreshToken)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 refresh token"));

        // JWT 재발급 시 nickname 포함
        AppUser user = userRepository.findByKakaoId(Long.valueOf(kakaoId))
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보 없음"));

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(kakaoId)
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .claim("kakaoId", kakaoId)
                .claim("nickname", user.getNickname())
                .build();

        String newAccessToken = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new TokenResponseDto(newAccessToken, refreshToken);
    }

    public void logout(String refreshToken) {
        refreshTokenService.delete(refreshToken);
    }
}
