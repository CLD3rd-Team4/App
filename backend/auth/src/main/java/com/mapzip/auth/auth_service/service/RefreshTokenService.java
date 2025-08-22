package com.mapzip.auth.auth_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final StringRedisTemplate redis;
    private static final Duration TTL = Duration.ofDays(1);

    private String key(String refreshToken) {
        return "rt:token:" + refreshToken;
    }

    // 발급/로그인 시 저장 (멀티 세션 허용)
    public void save(String refreshToken, String kakaoId) {
        redis.opsForValue().set(key(refreshToken), kakaoId, TTL);
    }

    // 리프레시 요청에서 토큰으로 사용자(kakaoId)
    public Optional<String> getUserIdFromRefreshToken(String refreshToken) {
        return Optional.ofNullable(redis.opsForValue().get(key(refreshToken)));
    }

    //단일 세션로그아웃: 그 토큰만 무효화
    public void delete(String refreshToken) {
        redis.delete(key(refreshToken));
    }
}

