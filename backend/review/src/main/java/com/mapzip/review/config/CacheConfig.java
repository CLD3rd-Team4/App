package com.mapzip.review.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${redis.host:localhost}")
    private String redisHost;

    @Value("${redis.port:6379}")
    private int redisPort;

    @Value("${redis.password:}")
    private String redisPassword;

    @Value("${redis.database:0}")
    private int database;

    @Value("${review.cache.user-reviews-ttl:3600}")
    private long userReviewsTtl;

    @Value("${review.cache.restaurant-reviews-ttl:7200}")
    private long restaurantReviewsTtl;

    @Value("${review.cache.review-stats-ttl:21600}")
    private long reviewStatsTtl;

    @Value("${review.cache.ocr-results-ttl:86400}")
    private long ocrResultsTtl;

    @Value("${review.cache.recommendation-ttl:14400}")
    private long recommendationTtl;

    /**
     * Redis 연결 팩토리 설정
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(redisHost);
        redisConfig.setPort(redisPort);
        redisConfig.setDatabase(database);
        
        if (redisPassword != null && !redisPassword.isEmpty()) {
            redisConfig.setPassword(redisPassword);
        }

        return new LettuceConnectionFactory(redisConfig);
    }

    /**
     * Redis Template 설정
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // JSON 직렬화를 위한 ObjectMapper 설정
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(
            objectMapper.getPolymorphicTypeValidator(),
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );

        // Key는 String, Value는 JSON으로 직렬화
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 캐시 매니저 설정 - 캐시별 TTL 및 설정 관리
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 기본 캐시 설정
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30)) // 기본 30분 TTL
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        // 캐시별 개별 설정 (Config Server 환경 변수 기반)
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // 사용자 리뷰 캐시
        cacheConfigurations.put("userReviews", defaultConfig
                .entryTtl(Duration.ofSeconds(getUserReviewsTtl())));
        
        // 식당 리뷰 캐시
        cacheConfigurations.put("restaurantReviews", defaultConfig
                .entryTtl(Duration.ofSeconds(getRestaurantReviewsTtl())));
        
        // 리뷰 통계 캐시
        cacheConfigurations.put("reviewStats", defaultConfig
                .entryTtl(Duration.ofSeconds(getReviewStatsTtl())));
        
        // OCR 결과 캐시
        cacheConfigurations.put("ocrResults", defaultConfig
                .entryTtl(Duration.ofSeconds(getOcrResultsTtl())));
        
        // 추천용 리뷰 캐시
        cacheConfigurations.put("recommendationReviews", defaultConfig
                .entryTtl(Duration.ofSeconds(getRecommendationTtl())));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    /**
     * 캐시 키 생성기 - 일관된 캐시 키 형식 보장
     */
    @Bean("customKeyGenerator")
    public org.springframework.cache.interceptor.KeyGenerator keyGenerator() {
        return (target, method, params) -> {
            StringBuilder key = new StringBuilder();
            key.append(target.getClass().getSimpleName())
               .append(":")
               .append(method.getName());
            
            for (Object param : params) {
                key.append(":").append(param != null ? param.toString() : "null");
            }
            
            return key.toString();
        };
    }

    // TTL 값 반환 메서드들
    private long getUserReviewsTtl() {
        return userReviewsTtl;
    }

    private long getRestaurantReviewsTtl() {
        return restaurantReviewsTtl;
    }

    private long getReviewStatsTtl() {
        return reviewStatsTtl;
    }

    private long getOcrResultsTtl() {
        return ocrResultsTtl;
    }

    private long getRecommendationTtl() {
        return recommendationTtl;
    }
}