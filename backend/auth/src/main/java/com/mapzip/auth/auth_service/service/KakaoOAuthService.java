package com.mapzip.auth.auth_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapzip.auth.auth_service.dto.TokenResponseDto;
import com.mapzip.auth.auth_service.entity.AppUser;
import com.mapzip.auth.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoOAuthService {

    private final UserRepository userRepository;
    private final JwtEncoder jwtEncoder;
    private final RefreshTokenService refreshTokenService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient;

    @Value("${kakao.client-id}")
    private String clientId;

    @Value("${kakao.redirect-uri}")
    private String redirectUri;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    public TokenResponseDto loginWithKakao(String code) {
        String kakaoAccessToken = getKakaoAccessToken(code);
        Long kakaoId = getKakaoUserId(kakaoAccessToken);

        AppUser user = userRepository.findByKakaoId(kakaoId)
                .orElseGet(() -> userRepository.save(AppUser.builder()
                        .kakaoId(kakaoId)
                        .nickname(null)
                        .build()));

        // 신규 회원인지 여부 판단
        boolean isNew = (user.getNickname() == null);

        String accessToken = generateAccessToken(kakaoId.toString());
        String refreshToken = generateAndStoreRefreshToken(kakaoId.toString());

        return new TokenResponseDto(accessToken, refreshToken, isNew);
    }

    private String getKakaoAccessToken(String code) {
        String response = webClient.post()
                .uri("https://kauth.kakao.com/oauth/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .bodyValue("grant_type=authorization_code" +
                        "&client_id=" + clientId +
                        "&redirect_uri=" + redirectUri +
                        "&code=" + code)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            return jsonNode.get("access_token").asText();
        } catch (Exception e) {
            throw new RuntimeException("카카오 access token 파싱 실패", e);
        }
    }

    private Long getKakaoUserId(String accessToken) {
        String response = webClient.get()
                .uri("https://kapi.kakao.com/v2/user/me")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            return jsonNode.get("id").asLong();  // ex) 12345678
        } catch (Exception e) {
            throw new RuntimeException("카카오 사용자 정보 파싱 실패", e);
        }
    }

    private String generateAccessToken(String kakaoId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(kakaoId)
                .claim("scope", "read write")
                .issuedAt(now)
                .expiresAt(now.plusMillis(jwtExpirationMs))
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private String generateAndStoreRefreshToken(String kakaoId) {
        String refreshToken = UUID.randomUUID().toString();
        refreshTokenService.save(refreshToken, kakaoId); // 예: 메모리 or Redis
        return refreshToken;
    }

    public TokenResponseDto reissueAccessToken(String refreshToken) {
        String kakaoId = refreshTokenService.getUserIdFromRefreshToken(refreshToken)
                .orElseThrow(() -> new RuntimeException("유효하지 않은 refresh token"));

        String newAccessToken = generateAccessToken(kakaoId);

        return new TokenResponseDto(newAccessToken, refreshToken, false);
    }

    public void logout(String refreshToken) {
        refreshTokenService.delete(refreshToken);
    }
}
