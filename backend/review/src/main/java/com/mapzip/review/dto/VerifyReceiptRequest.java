package com.mapzip.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * 영수증 검증 요청 DTO
 */
public class VerifyReceiptRequest {
    
    @NotNull(message = "영수증 이미지는 필수입니다")
    private MultipartFile receiptImage;
    
    @NotBlank(message = "예상 식당명은 필수입니다")
    @Size(max = 100, message = "식당명은 100자를 초과할 수 없습니다")
    private String expectedRestaurantName;
    
    @NotBlank(message = "예상 주소는 필수입니다")
    @Size(max = 200, message = "주소는 200자를 초과할 수 없습니다")
    private String expectedAddress;
    
    public VerifyReceiptRequest() {}
    
    public VerifyReceiptRequest(MultipartFile receiptImage, String expectedRestaurantName, String expectedAddress) {
        this.receiptImage = receiptImage;
        this.expectedRestaurantName = expectedRestaurantName;
        this.expectedAddress = expectedAddress;
    }
    
    // Getters and Setters
    public MultipartFile getReceiptImage() {
        return receiptImage;
    }
    
    public void setReceiptImage(MultipartFile receiptImage) {
        this.receiptImage = receiptImage;
    }
    
    public String getExpectedRestaurantName() {
        return expectedRestaurantName;
    }
    
    public void setExpectedRestaurantName(String expectedRestaurantName) {
        this.expectedRestaurantName = expectedRestaurantName;
    }
    
    public String getExpectedAddress() {
        return expectedAddress;
    }
    
    public void setExpectedAddress(String expectedAddress) {
        this.expectedAddress = expectedAddress;
    }
}