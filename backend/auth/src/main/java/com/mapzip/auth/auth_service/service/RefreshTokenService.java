package com.mapzip.auth.auth_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;

    public void save(String refreshToken, String kakaoIdAsString) {
        System.out.println("refreshTokenService의 save에서 진입");

        try {
            redisTemplate.opsForValue().set(refreshToken, kakaoIdAsString, Duration.ofDays(1));
            System.out.println("Redis 저장 성공");
        } catch (Exception e) {
            System.out.println("Redis 저장 실패");
            e.printStackTrace(); // 예외 로그 확인
        }
    }


    public Optional<String> getUserIdFromRefreshToken(String token) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(token));
    }

    public void delete(String refreshToken) {
        redisTemplate.delete(refreshToken);
    }
}

