package com.mapzip.review.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class S3Service {
    
    private static final Logger logger = LoggerFactory.getLogger(S3Service.class);
    
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    
    @Value("${aws.s3.bucket-name}")
    private String bucketName;
    
    @Value("${review.image.max-file-size:10485760}") // 10MB
    private long maxFileSize;
    
    @Value("${review.image.allowed-types:image/jpeg,image/png,image/jpg}")
    private List<String> allowedTypes;
    
    @Value("${aws.s3.presign-duration:PT1H}") // 1시간
    private Duration presignDuration;
    
    public S3Service() {
        this.s3Client = S3Client.builder().build();
        this.s3Presigner = S3Presigner.builder().build();
    }
    
    public String uploadImage(byte[] imageData, String contentType, String userId) {
        try {
            // 1. 파일 검증
            validateImageFile(imageData, contentType);
            
            // 2. 파일명 생성
            String fileName = generateFileName(userId, contentType);
            String keyName = "reviews/" + fileName;
            
            // 3. S3 업로드 (Private 버킷)
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(keyName)
                    .contentType(contentType)
                    .build();
            
            s3Client.putObject(putRequest, RequestBody.fromBytes(imageData));
            logger.info("Image uploaded successfully: {}", keyName);
            
            // 4. Pre-signed URL 생성 (보안)
            return generatePresignedUrl(keyName);
            
        } catch (Exception e) {
            logger.error("Failed to upload image to S3", e);
            throw new RuntimeException("이미지 업로드 실패: " + e.getMessage());
        }
    }
    
    /**
     * 파일 검증 (보안)
     */
    private void validateImageFile(byte[] imageData, String contentType) {
        // 1. 파일 크기 검증
        if (imageData.length > maxFileSize) {
            throw new IllegalArgumentException(
                String.format("파일 크기가 너무 큽니다. 최대 %dMB까지 업로드 가능합니다.", 
                    maxFileSize / (1024 * 1024)));
        }
        
        // 2. MIME 타입 검증
        if (!allowedTypes.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                "지원하지 않는 파일 형식입니다. JPEG, PNG, JPG만 업로드 가능합니다.");
        }
        
        // 3. 파일 시그니처 검증 (Magic Number)
        if (!isValidImageSignature(imageData)) {
            throw new IllegalArgumentException("유효하지 않은 이미지 파일입니다.");
        }
    }
    
    /**
     * 파일 시그니처 검증
     */
    private boolean isValidImageSignature(byte[] data) {
        if (data.length < 4) return false;
        
        // JPEG: FF D8 FF
        if (data[0] == (byte)0xFF && data[1] == (byte)0xD8 && data[2] == (byte)0xFF) {
            return true;
        }
        
        // PNG: 89 50 4E 47
        if (data[0] == (byte)0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Pre-signed URL 생성 (보안)
     */
    private String generatePresignedUrl(String keyName) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(keyName)
                    .build();
            
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(presignDuration) // 1시간 유효
                    .getObjectRequest(getObjectRequest)
                    .build();
            
            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            String presignedUrl = presignedRequest.url().toString();
            
            logger.info("Pre-signed URL generated for key: {}", keyName);
            return presignedUrl;
            
        } catch (Exception e) {
            logger.error("Failed to generate pre-signed URL for key: {}", keyName, e);
            throw new RuntimeException("이미지 URL 생성 실패: " + e.getMessage());
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