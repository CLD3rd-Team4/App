package mapzip.gateway.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private String secret = "test-secret-key-for-jwt-validation-testing-purposes";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(secret);
    }

    @Test
    void shouldReturnValidForValidToken() {
        // Given
        String validToken = createValidToken("testuser", new Date(System.currentTimeMillis() + 3600000));

        // When
        TokenValidationResult result = jwtUtil.validateTokenWithResult(validToken);

        // Then
        assertEquals(TokenValidationResult.VALID, result);
    }

    @Test
    void shouldReturnExpiredForExpiredToken() {
        // Given
        String expiredToken = createValidToken("testuser", new Date(System.currentTimeMillis() - 3600000));

        // When
        TokenValidationResult result = jwtUtil.validateTokenWithResult(expiredToken);

        // Then
        assertEquals(TokenValidationResult.EXPIRED, result);
    }

    @Test
    void shouldReturnInvalidForMalformedToken() {
        // Given
        String invalidToken = "invalid.token.format";

        // When
        TokenValidationResult result = jwtUtil.validateTokenWithResult(invalidToken);

        // Then
        assertEquals(TokenValidationResult.INVALID, result);
    }

    @Test
    void shouldReturnInvalidForWrongSignature() {
        // Given
        String wrongSecret = "wrong-secret-key";
        SecretKey wrongKey = Keys.hmacShaKeyFor(wrongSecret.getBytes());
        String tokenWithWrongSignature = Jwts.builder()
                .subject("testuser")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(wrongKey)
                .compact();

        // When
        TokenValidationResult result = jwtUtil.validateTokenWithResult(tokenWithWrongSignature);

        // Then
        assertEquals(TokenValidationResult.INVALID, result);
    }

    private String createValidToken(String userId, Date expiration) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        return Jwts.builder()
                .subject(userId)
                .issuedAt(new Date())
                .expiration(expiration)
                .signWith(key)
                .compact();
    }
}
