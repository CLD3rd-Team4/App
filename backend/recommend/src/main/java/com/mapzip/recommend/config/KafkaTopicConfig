package com.mapzip.recommend.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    // recommend-request
    @Bean
    public NewTopic recommendRequestTopic() {
        return TopicBuilder.name("recommend-request")
                .partitions(3)      // 브로커 수에 맞게
                .replicas(3)        // 복제수 (MSK 3대면 3 권장)
                .build();
    }

    // recommend-result
    @Bean
    public NewTopic recommendResultTopic() {
        return TopicBuilder.name("recommend-result")
                .partitions(3)
                .replicas(3)
                .build();
    }
}
