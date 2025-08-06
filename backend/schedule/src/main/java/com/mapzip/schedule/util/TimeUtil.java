package com.mapzip.schedule.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class TimeUtil {

    private static final DateTimeFormatter KOREAN_AM_PM_FORMATTER = DateTimeFormatter.ofPattern("a hh:mm", Locale.KOREAN);
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
    private static final DateTimeFormatter TMAP_RESPONSE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    /**
     * "오전/오후 hh:mm" 형식의 문자열을 특정 기준 날짜에 맞는 LocalDateTime 객체로 변환합니다.
     */
    public static LocalDateTime parseKoreanAmPmToFuture(String timeStr, LocalDate baseDate) {
        LocalDateTime parsedDateTime;
        try {
            LocalTime localTime = LocalTime.parse(timeStr, KOREAN_AM_PM_FORMATTER);
            parsedDateTime = baseDate.atTime(localTime);
        } catch (Exception e) {
            // "HH:mm" 형식도 지원
            parsedDateTime = baseDate.atTime(LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm")));
        }

        return parsedDateTime;
    }

    /**
     * LocalDateTime 객체를 Tmap API가 요구하는 ISO 형식 문자열로 변환합니다.
     */
    public static String toTmapApiFormat(LocalDateTime dateTime) {
        return dateTime.atZone(SEOUL_ZONE_ID).format(ISO_FORMATTER);
    }

    public static DateTimeFormatter getTmapResponseFormatter() {
        return TMAP_RESPONSE_FORMATTER;
    }

    /**
     * LocalDateTime 객체를 "오후 HH:mm" 형식의 문자열로 변환합니다.
     */
    public static String toKoreanAmPm(LocalDateTime dateTime) {
        return dateTime.format(KOREAN_AM_PM_FORMATTER);
    }

    /**
     * 현재 서울의 시간을 반환합니다.
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(SEOUL_ZONE_ID);
    }
    
}
