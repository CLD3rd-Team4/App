package com.mapzip.review.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import com.mapzip.review.dto.OcrResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OcrService {
    
    private static final Logger logger = LoggerFactory.getLogger(OcrService.class);
    
    @Value("${google.cloud.vision.api-key:}")
    private String serviceAccountKey;
    
    @Value("${google.cloud.vision.credentials-path:}")
    private String credentialsPath;
    
    private static final Pattern DATE_PATTERN = 
        Pattern.compile("(\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}|\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{4})");
    
    private static final Pattern AMOUNT_PATTERN = 
        Pattern.compile("(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})?|\\d+)원?");
    
    public OcrResultDto processReceiptImage(byte[] imageData, 
                                          String expectedRestaurantName, 
                                          String expectedAddress) {
        try {
            // Google Cloud Vision API 클라이언트 생성 (운영 환경 대응)
            GoogleCredentials credentials;
            
            if (serviceAccountKey != null && !serviceAccountKey.isEmpty()) {
                // Config Server 암호화된 서비스 계정 키 JSON 사용
                try {
                    logger.info("Config Server 서비스 계정 키 사용");
                    credentials = ServiceAccountCredentials.fromStream(
                        new ByteArrayInputStream(serviceAccountKey.getBytes())
                    );
                } catch (Exception e) {
                    logger.error("서비스 계정 키 파싱 실패: {}", e.getMessage());
                    throw new RuntimeException("구글 클라우드 비전 API 인증 실패", e);
                }
            } else if (credentialsPath != null && !credentialsPath.isEmpty()) {
                // Kubernetes Secret 마운트된 파일 경로 사용
                try {
                    logger.info("Kubernetes Secret 서비스 계정 키 사용: {}", credentialsPath);
                    credentials = ServiceAccountCredentials.fromStream(
                        new ByteArrayInputStream(java.nio.file.Files.readAllBytes(
                            java.nio.file.Paths.get(credentialsPath)))
                    );
                } catch (Exception e) {
                    logger.error("서비스 계정 키 파일 읽기 실패: {}", e.getMessage());
                    throw new RuntimeException("구글 클라우드 비전 API 인증 실패", e);
                }
            } else {
                // 어떤 인증 정보도 없는 경우 오류 발생
                throw new IllegalStateException("구글 클라우드 비전 API 인증 정보가 설정되지 않았습니다. " +
                    "google.cloud.vision.api-key 또는 google.cloud.vision.credentials-path를 설정해주세요.");
            }
            
            final GoogleCredentials finalCredentials = credentials;
            
            ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                    .setCredentialsProvider(() -> finalCredentials)
                    .build();
            
            try (ImageAnnotatorClient vision = ImageAnnotatorClient.create(settings)) {
                // 이미지 준비
                ByteString imgBytes = ByteString.copyFrom(imageData);
                Image img = Image.newBuilder().setContent(imgBytes).build();
                Feature feat = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build();
                AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .addFeatures(feat)
                    .setImage(img)
                    .build();
                
                // OCR 수행
                BatchAnnotateImagesResponse response = vision.batchAnnotateImages(
                    BatchAnnotateImagesRequest.newBuilder()
                        .addRequests(request)
                        .build());
                
                List<AnnotateImageResponse> responses = response.getResponsesList();
                
                if (responses.isEmpty() || responses.get(0).hasError()) {
                    logger.error("OCR failed: {}", 
                        responses.isEmpty() ? "No response" : responses.get(0).getError());
                    return createFailedResult("OCR 처리 실패");
                }
                
                // 텍스트 추출
                String extractedText = responses.get(0).getFullTextAnnotation().getText();
                logger.debug("Extracted text: {}", extractedText);
                
                return analyzeReceiptText(extractedText, expectedRestaurantName, expectedAddress);
                
            }
        } catch (IOException e) {
            logger.error("OCR service error", e);
            return createFailedResult("OCR 서비스 오류: " + e.getMessage());
        }
    }
    
    private OcrResultDto analyzeReceiptText(String text, 
                                          String expectedRestaurantName, 
                                          String expectedAddress) {
        OcrResultDto result = new OcrResultDto();
        result.setRawText(text);
        
        // 텍스트를 줄별로 분리
        String[] lines = text.split("\\n");
        
        // 식당명 추출 (첫 번째 줄 또는 특정 패턴)
        String extractedRestaurantName = extractRestaurantName(lines);
        result.setRestaurantName(extractedRestaurantName);
        
        // 주소 추출
        String extractedAddress = extractAddress(lines);
        result.setAddress(extractedAddress);
        
        // 방문 날짜 추출
        String visitDate = extractVisitDate(text);
        result.setVisitDate(visitDate);
        
        // 총 금액 추출
        String totalAmount = extractTotalAmount(text);
        result.setTotalAmount(totalAmount);
        
        // 검증 수행
        boolean isValid = validateReceipt(
            extractedRestaurantName, extractedAddress, 
            expectedRestaurantName, expectedAddress);
        
        result.setValid(isValid);
        result.setConfidence(calculateConfidence(extractedRestaurantName, extractedAddress, 
                                               expectedRestaurantName, expectedAddress));
        
        return result;
    }
    
    private String extractRestaurantName(String[] lines) {
        // 일반적으로 영수증의 첫 번째 줄이 상호명
        if (lines.length > 0) {
            String firstLine = lines[0].trim();
            // 특수문자나 숫자가 많이 포함된 경우 다음 줄 확인
            if (firstLine.length() > 2 && !firstLine.matches(".*\\d{3,}.*")) {
                return firstLine;
            }
        }
        
        // 첫 번째 줄이 적절하지 않은 경우 다른 줄에서 찾기
        for (int i = 1; i < Math.min(lines.length, 5); i++) {
            String line = lines[i].trim();
            if (line.length() > 2 && !line.matches(".*\\d{3,}.*") && 
                !line.contains("주소") && !line.contains("전화")) {
                return line;
            }
        }
        
        return lines.length > 0 ? lines[0].trim() : "";
    }
    
    private String extractAddress(String[] lines) {
        for (String line : lines) {
            if (line.contains("주소") || line.contains("서울") || line.contains("경기") || 
                line.contains("부산") || line.contains("대구") || line.contains("인천") ||
                line.contains("광주") || line.contains("대전") || line.contains("울산")) {
                return line.trim();
            }
        }
        return "";
    }
    
    private String extractVisitDate(String text) {
        Matcher matcher = DATE_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }
    
    private String extractTotalAmount(String text) {
        // "합계", "총액", "결제금액" 등의 키워드 근처에서 금액 찾기
        String[] keywords = {"합계", "총액", "결제금액", "총 금액", "계"};
        String[] lines = text.split("\\n");
        
        for (String keyword : keywords) {
            for (String line : lines) {
                if (line.contains(keyword)) {
                    Matcher matcher = AMOUNT_PATTERN.matcher(line);
                    if (matcher.find()) {
                        return matcher.group(1);
                    }
                }
            }
        }
        
        // 키워드를 찾지 못한 경우 가장 큰 금액 반환
        List<String> amounts = new ArrayList<>();
        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        while (matcher.find()) {
            amounts.add(matcher.group(1));
        }
        
        return amounts.stream()
            .filter(amount -> amount.replaceAll("[^\\d]", "").length() >= 4) // 최소 4자리
            .max((a, b) -> {
                try {
                    int amountA = Integer.parseInt(a.replaceAll("[^\\d]", ""));
                    int amountB = Integer.parseInt(b.replaceAll("[^\\d]", ""));
                    return Integer.compare(amountA, amountB);
                } catch (NumberFormatException e) {
                    logger.warn("Failed to parse amount: {} or {}", a, b);
                    return 0; // 파싱 실패 시 동일한 값으로 처리
                }
            })
            .orElse("");
    }
    
    private boolean validateReceipt(String extractedRestaurantName, String extractedAddress,
                                  String expectedRestaurantName, String expectedAddress) {
        
        // 식당명 유사도 검사
        double nameSimilarity = calculateSimilarity(extractedRestaurantName, expectedRestaurantName);
        
        // 주소 유사도 검사 (주소가 있는 경우)
        double addressSimilarity = 0.5; // 기본값
        if (extractedAddress != null && !extractedAddress.isEmpty() && 
            expectedAddress != null && !expectedAddress.isEmpty()) {
            addressSimilarity = calculateSimilarity(extractedAddress, expectedAddress);
        }
        
        // 종합 점수가 0.6 이상이면 검증 통과
        return (nameSimilarity * 0.7 + addressSimilarity * 0.3) >= 0.6;
    }
    
    private double calculateConfidence(String extractedRestaurantName, String extractedAddress,
                                     String expectedRestaurantName, String expectedAddress) {
        double nameSimilarity = calculateSimilarity(extractedRestaurantName, expectedRestaurantName);
        double addressSimilarity = 0.5;
        
        if (extractedAddress != null && !extractedAddress.isEmpty() && 
            expectedAddress != null && !expectedAddress.isEmpty()) {
            addressSimilarity = calculateSimilarity(extractedAddress, expectedAddress);
        }
        
        return nameSimilarity * 0.7 + addressSimilarity * 0.3;
    }
    
    private double calculateSimilarity(String str1, String str2) {
        if (str1 == null || str2 == null) return 0.0;
        if (str1.equals(str2)) return 1.0;
        
        // 간단한 유사도 계산 (Levenshtein distance 기반)
        int distance = levenshteinDistance(str1.toLowerCase(), str2.toLowerCase());
        int maxLen = Math.max(str1.length(), str2.length());
        
        return maxLen == 0 ? 1.0 : 1.0 - (double) distance / maxLen;
    }
    
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + (s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1)
                    );
                }
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
    
    private OcrResultDto createFailedResult(String message) {
        OcrResultDto result = new OcrResultDto();
        result.setValid(false);
        result.setConfidence(0.0);
        result.setRawText(message);
        return result;
    }
    
    /**
     * OCR 영수증 날짜와 실제 방문 날짜를 비교하여 검증
     * @param ocrVisitDate OCR에서 추출된 방문 날짜
     * @param actualVisitDate 실제 방문 날짜 (사용자가 입력한 날짜)
     * @return 검증 결과
     */
    public boolean validateVisitDate(String ocrVisitDate, String actualVisitDate) {
        if (ocrVisitDate == null || actualVisitDate == null) {
            logger.warn("날짜 정보가 없음 - OCR: {}, Actual: {}", ocrVisitDate, actualVisitDate);
            return false;
        }
        
        try {
            LocalDate ocrDate = parseDate(ocrVisitDate);
            LocalDate actualDate = parseDate(actualVisitDate);
            
            if (ocrDate == null || actualDate == null) {
                logger.warn("날짜 파싱 실패 - OCR: {}, Actual: {}", ocrVisitDate, actualVisitDate);
                return false;
            }
            
            // OCR 영수증 날짜가 실제 방문 날짜보다 과거면 false
            if (ocrDate.isBefore(actualDate)) {
                logger.warn("영수증 날짜({})가 방문 날짜({})보다 과거입니다", ocrDate, actualDate);
                return false;
            }
            
            // OCR 영수증 날짜가 방문 날짜보다 너무 미래면 false (7일 이상 차이)
            if (ocrDate.isAfter(actualDate.plusDays(7))) {
                logger.warn("영수증 날짜({})가 방문 날짜({})보다 7일 이상 미래입니다", ocrDate, actualDate);
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            logger.error("날짜 검증 중 오류 발생", e);
            return false;
        }
    }
    
    /**
     * 다양한 날짜 형식을 파싱하여 LocalDate로 변환
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        String cleanDate = dateStr.trim();
        
        // 다양한 날짜 형식 시도
        String[] patterns = {
            "yyyy-MM-dd",
            "yyyy/MM/dd", 
            "yyyy.MM.dd",
            "MM-dd-yyyy",
            "MM/dd/yyyy",
            "dd-MM-yyyy",
            "dd/MM/yyyy",
            "dd.MM.yyyy"
        };
        
        for (String pattern : patterns) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                return LocalDate.parse(cleanDate, formatter);
            } catch (DateTimeParseException e) {
                // 다음 패턴 시도
            }
        }
        
        logger.warn("지원되지 않는 날짜 형식: {}", cleanDate);
        return null;
    }
}