package com.mapzip.review.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicLong;

@Configuration
public class MetricsConfig {

    private final AtomicLong activeReviewCreations = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    /**
     * 공통 태그 설정 (Config Server 패턴)
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config().commonTags(
            "service", "review",
            "version", "1.0.0"
        );
    }

    /**
     * 리뷰 생성 관련 메트릭
     */
    @Bean
    public Counter reviewCreatedCounter(MeterRegistry meterRegistry) {
        return Counter.builder("review_created_total")
                .description("Total number of reviews created")
                .tag("service", "review")
                .register(meterRegistry);
    }

    @Bean
    public Counter reviewVerifiedCounter(MeterRegistry meterRegistry) {
        return Counter.builder("review_verified_total")
                .description("Total number of OCR verified reviews")
                .tag("service", "review")
                .register(meterRegistry);
    }

    @Bean
    public Timer reviewCreationTimer(MeterRegistry meterRegistry) {
        return Timer.builder("review_creation_duration")
                .description("Time taken to create a review")
                .tag("service", "review")
                .register(meterRegistry);
    }

    /**
     * OCR 처리 관련 메트릭
     */
    @Bean
    public Counter ocrProcessedCounter(MeterRegistry meterRegistry) {
        return Counter.builder("ocr_processed_total")
                .description("Total number of OCR processing attempts")
                .tag("service", "review")
                .register(meterRegistry);
    }

    @Bean
    public Counter ocrSuccessCounter(MeterRegistry meterRegistry) {
        return Counter.builder("ocr_success_total")
                .description("Total number of successful OCR processing")
                .tag("service", "review")
                .register(meterRegistry);
    }

    @Bean
    public Timer ocrProcessingTimer(MeterRegistry meterRegistry) {
        return Timer.builder("ocr_processing_duration")
                .description("Time taken to process OCR")
                .tag("service", "review")
                .register(meterRegistry);
    }

    /**
     * 캐시 관련 메트릭
     */
    @Bean
    public Counter cacheHitCounter(MeterRegistry meterRegistry) {
        return Counter.builder("cache_hits_total")
                .description("Total number of cache hits")
                .tag("service", "review")
                .register(meterRegistry);
    }

    @Bean
    public Counter cacheMissCounter(MeterRegistry meterRegistry) {
        return Counter.builder("cache_misses_total")
                .description("Total number of cache misses")
                .tag("service", "review")
                .register(meterRegistry);
    }

    @Bean
    public Gauge cacheHitRatioGauge(MeterRegistry meterRegistry) {
        return Gauge.builder("cache_hit_ratio", this, metrics -> {
                    long hits = cacheHits.get();
                    long misses = cacheMisses.get();
                    long total = hits + misses;
                    return total > 0 ? (double) hits / total : 0.0;
                })
                .description("Cache hit ratio")
                .tag("service", "review")
                .register(meterRegistry);
    }

    /**
     * DynamoDB 관련 메트릭
     */
    @Bean
    public Counter dynamodbReadCounter(MeterRegistry meterRegistry) {
        return Counter.builder("dynamodb_reads_total")
                .description("Total number of DynamoDB read operations")
                .tag("service", "review")
                .tag("table", "review")
                .register(meterRegistry);
    }

    @Bean
    public Counter dynamodbWriteCounter(MeterRegistry meterRegistry) {
        return Counter.builder("dynamodb_writes_total")
                .description("Total number of DynamoDB write operations")
                .tag("service", "review") 
                .tag("table", "review")
                .register(meterRegistry);
    }

    @Bean
    public Timer dynamodbQueryTimer(MeterRegistry meterRegistry) {
        return Timer.builder("dynamodb_query_duration")
                .description("Time taken for DynamoDB queries")
                .tag("service", "review")
                .register(meterRegistry);
    }

    /**
     * gRPC 관련 메트릭
     */
    @Bean
    public Counter grpcRequestCounter(MeterRegistry meterRegistry) {
        return Counter.builder("grpc_requests_total")
                .description("Total number of gRPC requests")
                .tag("service", "review")
                .register(meterRegistry);
    }

    @Bean
    public Timer grpcRequestTimer(MeterRegistry meterRegistry) {
        return Timer.builder("grpc_request_duration")
                .description("Time taken to handle gRPC requests")
                .tag("service", "review")
                .register(meterRegistry);
    }

    /**
     * S3 업로드 관련 메트릭
     */
    @Bean
    public Counter s3UploadCounter(MeterRegistry meterRegistry) {
        return Counter.builder("s3_uploads_total")
                .description("Total number of S3 upload operations")
                .tag("service", "review")
                .register(meterRegistry);
    }

    @Bean
    public Timer s3UploadTimer(MeterRegistry meterRegistry) {
        return Timer.builder("s3_upload_duration")
                .description("Time taken for S3 uploads")
                .tag("service", "review")
                .register(meterRegistry);
    }

    /**
     * 비즈니스 메트릭
     */
    @Bean
    public Gauge activeReviewCreationsGauge(MeterRegistry meterRegistry) {
        return Gauge.builder("active_review_creations", activeReviewCreations, AtomicLong::get)
                .description("Number of currently active review creation processes")
                .tag("service", "review")
                .register(meterRegistry);
    }

    /**
     * 추천 서버용 리뷰 조회 메트릭
     */
    @Bean
    public Counter recommendationQueryCounter(MeterRegistry meterRegistry) {
        return Counter.builder("recommendation_queries_total")
                .description("Total number of recommendation service queries")
                .tag("service", "review")
                .register(meterRegistry);
    }

    @Bean
    public Timer recommendationQueryTimer(MeterRegistry meterRegistry) {
        return Timer.builder("recommendation_query_duration")
                .description("Time taken for recommendation service queries")
                .tag("service", "review")
                .register(meterRegistry);
    }

    // 메트릭 업데이트용 헬퍼 메서드들
    public void incrementCacheHits() {
        cacheHits.incrementAndGet();
    }

    public void incrementCacheMisses() {
        cacheMisses.incrementAndGet();
    }

    public void incrementActiveReviewCreations() {
        activeReviewCreations.incrementAndGet();
    }

    public void decrementActiveReviewCreations() {
        activeReviewCreations.decrementAndGet();
    }
}