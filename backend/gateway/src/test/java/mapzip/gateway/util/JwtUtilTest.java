//package mapzip.gateway.util;
//import io.jsonwebtoken.security.Keys;
//import io.jsonwebtoken.Claims;
//import org.junit.jupiter.api.Test;
//
//import javax.crypto.SecretKey;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//class JwtUtilTest {
//
//    @Test
//    void shouldValidateActualTokenAndExtractUserId() {
//        // Given
//        String secret = "테스트 시에만 추가";
//        Long expiration = 86400000L;
//
//        JwtUtil jwtUtil = new JwtUtil(secret, expiration);
//
//        // 실제 생성한 유효한 토큰
//        String token = "..";
//
//        // When
//        boolean isValid = jwtUtil.validateToken(token);
//        String userId = jwtUtil.extractUserId(token);
//
//        // Then
//        assertTrue(isValid, "JWT 토큰이 유효해야 함");
//        assertEquals("testuser", userId, "사용자 ID가 일치해야 함");
//    }
//}
