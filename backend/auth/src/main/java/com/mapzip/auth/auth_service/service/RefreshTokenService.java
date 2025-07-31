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
        redisTemplate.opsForValue().set(refreshToken, kakaoIdAsString, Duration.ofDays(7));
    }

    public Optional<String> getUserIdFromRefreshToken(String token) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(token));
    }

    public void delete(String refreshToken) {
        redisTemplate.delete(refreshToken);
    }
}

