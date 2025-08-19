package com.mapzip.recommend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleanupDbService {

    private final StringRedisTemplate redis;

    public void cleanupUserKeysExceptSchedule(String userId, String keepScheduleId) {
        if (isBlank(userId) || isBlank(keepScheduleId)) {
            log.warn("cleanupUserKeysExceptSchedule: userId or keepScheduleId is blank. userId='{}', keep='{}'",
                    userId, keepScheduleId);
            return;
        }

        String[] patterns = new String[] {
            "recommend:" + userId + ":*",
            "scheduleDetail:" + userId + ":*"
            // "selectedPlaces:" + userId + ":*" // 필요 시 추가
        };

        final int BATCH_SIZE = 500;

        // ★ 여기서 RedisCallback 으로 명시 캐스팅하여 모호성 제거
        redis.execute((RedisCallback<Object>) connection -> {
            int deletedTotal = 0;

            for (String pattern : patterns) {
                int deletedForPattern = 0;

                ScanOptions options = ScanOptions.scanOptions()
                        .match(pattern)
                        .count(1000)
                        .build();

                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    ArrayList<byte[]> batch = new ArrayList<>(BATCH_SIZE);

                    while (cursor.hasNext()) {
                        byte[] key = cursor.next();
                        String keyStr = new String(key, StandardCharsets.UTF_8);

                        if (shouldKeepKey(keyStr, keepScheduleId)) {
                            continue;
                        }

                        batch.add(key);
                        if (batch.size() >= BATCH_SIZE) {
                            long delCount = connection.del(batch.toArray(new byte[0][]));
                            deletedForPattern += delCount;
                            batch.clear();
                        }
                    }

                    if (!batch.isEmpty()) {
                        long delCount = connection.del(batch.toArray(new byte[0][]));
                        deletedForPattern += delCount;
                    }
                } catch (Exception e) {
                    log.warn("Redis SCAN/DEL 실패 pattern='{}'", pattern, e);
                }

                deletedTotal += deletedForPattern;
                if (deletedForPattern > 0) {
                    log.info("Redis 정리: pattern='{}' 삭제 {}개", pattern, deletedForPattern);
                }
            }

            log.info("Redis 정리 완료 userId={}, keepScheduleId={}, 총 삭제={}", userId, keepScheduleId, deletedTotal);
            return null; // RedisCallback<Object>의 반환값
        });
    }

    private boolean shouldKeepKey(String key, String keepScheduleId) {
        String[] parts = key.split(":");
        // 기대 형태: prefix:userId:scheduleId(:...)
        if (parts.length >= 3) {
            return Objects.equals(keepScheduleId, parts[2]);
        }
        return false;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
