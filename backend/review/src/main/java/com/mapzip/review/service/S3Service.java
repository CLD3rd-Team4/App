package com.mapzip.review.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

@Service
public class S3Service {
    
    private static final Logger logger = LoggerFactory.getLogger(S3Service.class);
    
    private final S3Client s3Client;
    
    @Value("${aws.s3.bucket}")
    private String bucketName;
    
    public S3Service() {
        this.s3Client = S3Client.builder().build();
    }
    
    public String uploadImage(byte[] imageData, String contentType, String userId) {
        try {
            String fileName = generateFileName(userId, contentType);
            String keyName = "reviews/" + fileName;
            
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(keyName)
                    .contentType(contentType)
                    .build();
            
            s3Client.putObject(putRequest, RequestBody.fromBytes(imageData));
            
            // S3 URL 반환
            String imageUrl = String.format("https://%s.s3.amazonaws.com/%s", bucketName, keyName);
            logger.info("Image uploaded successfully: {}", imageUrl);
            
            return imageUrl;
            
        } catch (Exception e) {
            logger.error("Failed to upload image to S3", e);
            throw new RuntimeException("이미지 업로드 실패: " + e.getMessage());
        }
    }
    
    private String generateFileName(String userId, String contentType) {
        String extension = getFileExtension(contentType);
        return String.format("%s_%s_%s.%s", 
                userId, 
                System.currentTimeMillis(), 
                UUID.randomUUID().toString().substring(0, 8),
                extension);
    }
    
    private String getFileExtension(String contentType) {
        switch (contentType.toLowerCase()) {
            case "image/jpeg":
            case "image/jpg":
                return "jpg";
            case "image/png":
                return "png";
            case "image/gif":
                return "gif";
            case "image/webp":
                return "webp";
            default:
                return "jpg";
        }
    }
}