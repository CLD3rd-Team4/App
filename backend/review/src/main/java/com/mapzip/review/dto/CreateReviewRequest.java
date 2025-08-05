package com.mapzip.review.dto;

import jakarta.validation.constraints.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public class CreateReviewRequest {
    
    @NotBlank(message = "식당 ID는 필수입니다")
    private String restaurantId;
    
    @NotBlank(message = "식당명은 필수입니다")
    @Size(max = 100, message = "식당명은 100자를 초과할 수 없습니다")
    private String restaurantName;
    
    @Size(max = 200, message = "식당 주소는 200자를 초과할 수 없습니다")
    private String restaurantAddress;
    
    @NotNull(message = "평점은 필수입니다")
    @Min(value = 1, message = "평점은 1점 이상이어야 합니다")
    @Max(value = 5, message = "평점은 5점 이하여야 합니다")
    private Integer rating;
    
    @NotBlank(message = "리뷰 내용은 필수입니다")
    @Size(min = 10, max = 1000, message = "리뷰 내용은 10자 이상 1000자 이하여야 합니다")
    private String content;
    
    @Size(max = 3, message = "영수증 이미지는 최대 3개까지 업로드 가능합니다")
    private List<MultipartFile> receiptImages;
    
    @Size(max = 5, message = "리뷰 이미지는 최대 5개까지 업로드 가능합니다")
    private List<MultipartFile> reviewImages;

    // 기본 생성자
    public CreateReviewRequest() {}

    // Getters and Setters
    public String getRestaurantId() {
        return restaurantId;
    }

    public void setRestaurantId(String restaurantId) {
        this.restaurantId = restaurantId;
    }

    public String getRestaurantName() {
        return restaurantName;
    }

    public void setRestaurantName(String restaurantName) {
        this.restaurantName = restaurantName;
    }

    public String getRestaurantAddress() {
        return restaurantAddress;
    }

    public void setRestaurantAddress(String restaurantAddress) {
        this.restaurantAddress = restaurantAddress;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<MultipartFile> getReceiptImages() {
        return receiptImages;
    }

    public void setReceiptImages(List<MultipartFile> receiptImages) {
        this.receiptImages = receiptImages;
    }

    public List<MultipartFile> getReviewImages() {
        return reviewImages;
    }

    public void setReviewImages(List<MultipartFile> reviewImages) {
        this.reviewImages = reviewImages;
    }
}