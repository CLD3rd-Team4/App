package com.mapzip.recommend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Configuration
public class BedrockConfig {
	

    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient() {
  
        return BedrockRuntimeClient.builder()
                .region(Region.US_EAST_1) // Claude 3는 us-east-1에서만 작동
                .credentialsProvider(ProfileCredentialsProvider.create("default"))
                .build(); // ~/.aws/credentials에서 자동으로 인증
    }
}