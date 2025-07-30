package com.mapzip.schedule.dto.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KakaoSearchResponse {
    private Meta meta;
    private List<Document> documents;
}
