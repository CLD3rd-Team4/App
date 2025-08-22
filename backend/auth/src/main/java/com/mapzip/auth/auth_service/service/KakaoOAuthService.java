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
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoOAuthService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final WebClient webClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${kakao.client-id}")
    private String clientId;

    @Value("${kakao.redirect-uri}")
    private String redirectUri;

    @Value("${kakao.client-secret:}")
    private String clientSecret;

    @Value("${jwt.secret}")
    private String jwtSecret;

    public TokenResponseDto loginWithKakao(String code) {
        String kakaoAccessToken = getKakaoAccessToken(code);
        KakaoUserInfo kakaoUserInfo = getKakaoUserInfo(kakaoAccessToken);

        AppUser user = userRepository.findByKakaoId(kakaoUserInfo.kakaoId())
                .orElseGet(() -> userRepository.save(AppUser.builder()
                        .kakaoId(kakaoUserInfo.kakaoId())
                        .nickname(kakaoUserInfo.nickname())
                        .build()));

        String accessToken = Jwts.builder()
                .setSubject(kakaoUserInfo.kakaoId().toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000)) // 1시간
                .claim("kakaoId", kakaoUserInfo.kakaoId().toString())
                .claim("nickname", kakaoUserInfo.nickname())
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()), SignatureAlgorithm.HS256)
                .compact();
        
        String refreshToken = UUID.randomUUID().toString();

        refreshTokenService.save(refreshToken, kakaoUserInfo.kakaoId().toString());

        return new TokenResponseDto(accessToken, refreshToken);
    }

    private String getKakaoAccessToken(String code) {

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
                            log.info("kakao 응답 오류");
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
        
        String newAccessToken = Jwts.builder()
                .setSubject(kakaoId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .claim("kakaoId", kakaoId)
                .claim("nickname", user.getNickname())
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()), SignatureAlgorithm.HS256)
                .compact();

        return new TokenResponseDto(newAccessToken, refreshToken);
    }

    public void logout(String refreshToken) {
        refreshTokenService.delete(refreshToken);
    }
}
