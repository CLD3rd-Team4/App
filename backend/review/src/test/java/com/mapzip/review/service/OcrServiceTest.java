package com.mapzip.review.service;

import com.mapzip.review.dto.OcrResultDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OCR 서비스 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
public class OcrServiceTest {

    @InjectMocks
    private OcrService ocrService;

    @BeforeEach
    void setUp() {
        // 테스트용 설정값 주입
        ReflectionTestUtils.setField(ocrService, "serviceAccountKey", "test-key");
    }

    @Test
    void testValidateVisitDate_ValidDates_ShouldReturnTrue() {
        // Given
        String ocrDate = "2024-01-15";
        String visitDate = "2024-01-15";

        // When
        boolean result = ocrService.validateVisitDate(ocrDate, visitDate);

        // Then
        assertTrue(result, "같은 날짜는 유효해야 함");
    }

    @Test
    void testValidateVisitDate_FutureDateReceipt_ShouldReturnFalse() {
        // Given - 영수증 날짜가 방문 날짜보다 미래
        String ocrDate = "2024-01-20";
        String visitDate = "2024-01-15";

        // When
        boolean result = ocrService.validateVisitDate(ocrDate, visitDate);

        // Then
        assertFalse(result, "영수증 날짜가 방문 날짜보다 미래면 무효해야 함");
    }

    @Test
    void testValidateVisitDate_TooOldReceipt_ShouldReturnFalse() {
        // Given - 영수증이 방문보다 8일 이상 오래됨
        String ocrDate = "2024-01-01";
        String visitDate = "2024-01-10";

        // When
        boolean result = ocrService.validateVisitDate(ocrDate, visitDate);

        // Then
        assertFalse(result, "영수증이 7일 이상 오래되면 무효해야 함");
    }

    @Test
    void testValidateVisitDate_WithinWeek_ShouldReturnTrue() {
        // Given - 7일 이내 영수증
        String ocrDate = "2024-01-10";
        String visitDate = "2024-01-15";

        // When
        boolean result = ocrService.validateVisitDate(ocrDate, visitDate);

        // Then
        assertTrue(result, "7일 이내 영수증은 유효해야 함");
    }

    @Test
    void testValidateVisitDate_InvalidDateFormat_ShouldReturnFalse() {
        // Given - 잘못된 날짜 형식
        String ocrDate = "invalid-date";
        String visitDate = "2024-01-15";

        // When
        boolean result = ocrService.validateVisitDate(ocrDate, visitDate);

        // Then
        assertFalse(result, "잘못된 날짜 형식은 무효해야 함");
    }

    @Test
    void testProcessRealReceiptImage() throws IOException {
        // This is an integration test that calls the actual Google Cloud Vision API.
        // It requires valid credentials (google.cloud.vision.api-key or google.cloud.vision.credentials-path)
        // to be configured in the test environment.
        
        // Given
        String expectedRestaurantName = "퇴촌농협하나로마트";
        String expectedAddress = "경기도 광주시 퇴촌면 광동로 52번길 7";
        byte[] imageData = Files.readAllBytes(Paths.get("receipt.jfif"));

        // When
        OcrResultDto result = ocrService.processReceiptImage(imageData, expectedRestaurantName, expectedAddress);

        // Then
        assertNotNull(result, "Result should not be null");
        assertTrue(result.isValid(), "Receipt should be considered valid after weight adjustment");
        System.out.println("OCR Raw Text: " + result.getRawText());
        System.out.println("Validation Result: " + result.isValid());
        System.out.println("Confidence Score: " + result.getConfidence());
    }
}
