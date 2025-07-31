package com.mapzip.auth.auth_service.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KakaoLoginRequestDto {
    private String code; // 프론트에서 받은 인가코드
}
