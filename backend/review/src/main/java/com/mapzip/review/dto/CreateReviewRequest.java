package com.mapzip.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 리뷰 생성 요청 DTO
 */
public class CreateReviewRequest {
    
    @NotBlank(message = "식당 ID는 필수입니다")
    private String restaurantId;
    
    @NotBlank(message = "식당명은 필수입니다")
    @Size(max = 100, message = "식당명은 100자를 초과할 수 없습니다")
    private String restaurantName;
    
    @NotBlank(message = "식당 주소는 필수입니다")
    @Size(max = 200, message = "식당 주소는 200자를 초과할 수 없습니다")
    private String restaurantAddress;
    
    @NotNull(message = "평점은 필수입니다")
    @Min(value = 1, message = "평점은 1 이상이어야 합니다")
    @Max(value = 5, message = "평점은 5 이하여야 합니다")
    private Integer rating;
    
    @NotBlank(message = "리뷰 내용은 필수입니다")
    @Size(max = 1000, message = "리뷰 내용은 1000자를 초과할 수 없습니다")
    private String content;
    
    // 선택적 파일들
    private List<MultipartFile> receiptImages;
    private List<MultipartFile> reviewImages;
    
    // 선택적 필드들
    @Size(max = 50, message = "예약 시간은 50자를 초과할 수 없습니다")
    private String scheduledTime;
    
    @Size(max = 20, message = "방문 날짜는 20자를 초과할 수 없습니다")
    private String visitDate;
    
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
    
    public String getScheduledTime() {
        return scheduledTime;
    }
    
    public void setScheduledTime(String scheduledTime) {
        this.scheduledTime = scheduledTime;
    }
    
    public String getVisitDate() {
        return visitDate;
    }
    
    public void setVisitDate(String visitDate) {
        this.visitDate = visitDate;
    }
}