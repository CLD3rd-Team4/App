package com.mapzip.recommend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Configuration
public class BedrockConfig {
	

   @Bean
    public BedrockRuntimeClient bedrockRuntimeClient(
            @Value("${aws.region:ap-northeast-2}") String region  // Config Server에서 외부화
    ) {
        return BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .credentialsProvider(software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider.create())
                .build();
    }
}
