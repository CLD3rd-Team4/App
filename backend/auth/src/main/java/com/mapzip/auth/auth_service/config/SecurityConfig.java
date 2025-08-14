package com.mapzip.auth.auth_service.config;

import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository;
import org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository;
import com.nimbusds.jose.JWSAlgorithm;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults()) // CORS 활성화
                .authorizeHttpRequests(auth -> auth
                        // 프리플라이트는 전역 허용
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 카카오 콜백은 비인증 허용
                        .requestMatchers(HttpMethod.POST, "/auth/kakao/callback").permitAll()
                        // 토큰 관련 엔드포인트(리프레시/로그아웃 등)
                        .requestMatchers("/auth/token/**").permitAll()
                        // 헬스체크
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated()
                )
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(List.of(
                "https://www.mapzip.shop", // dev
                "http://localhost:3000"    // 로컬
        ));
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setAllowCredentials(true);   // 쿠키 전송 허용
        c.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
        s.registerCorsConfiguration("/**", c);
        return s;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator(JwtEncoder jwtEncoder) {
        OAuth2AccessTokenGenerator accessTokenGenerator = new OAuth2AccessTokenGenerator();
        OAuth2RefreshTokenGenerator refreshTokenGenerator = new OAuth2RefreshTokenGenerator();
        JwtGenerator jwtGenerator = new JwtGenerator(jwtEncoder);
        return new DelegatingOAuth2TokenGenerator(jwtGenerator, accessTokenGenerator, refreshTokenGenerator);
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(@Value("${jwt.secret}") String jwtSecret) {
        try {
            log.info("JWT HMAC 키 생성 시작 - 알고리즘: HS256, 키 길이: {} bytes", jwtSecret.getBytes().length);
            
            // 키 길이가 32바이트(256비트) 미만이면 패딩
            byte[] keyBytes = jwtSecret.getBytes();
            if (keyBytes.length < 32) {
                byte[] paddedKey = new byte[32];
                System.arraycopy(keyBytes, 0, paddedKey, 0, keyBytes.length);
                keyBytes = paddedKey;
                log.info("키 길이를 32바이트로 패딩 완료");
            }
            
            OctetSequenceKey hmacKey = new OctetSequenceKey.Builder(keyBytes)
                    .keyID("auth-hmac-key")
                    .algorithm(com.nimbusds.jose.JWSAlgorithm.HS256)
                    .keyUse(com.nimbusds.jose.jwk.KeyUse.SIGNATURE)
                    .build();
            
            log.info("JWT HMAC 키 생성 완료 - keyID: {}, 알고리즘: {}", hmacKey.getKeyID(), hmacKey.getAlgorithm());
            
            JWKSet jwkSet = new JWKSet(hmacKey);
            return new ImmutableJWKSet<>(jwkSet);
        } catch (Exception e) {
            log.error("JWT HMAC 키 생성 실패", e);
            throw new RuntimeException("JWT 키 생성 실패", e);
        }
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public HttpExchangeRepository httpExchangeRepository() {
        InMemoryHttpExchangeRepository repo = new InMemoryHttpExchangeRepository();
        repo.setCapacity(1000); // 보관 개수
        return repo;
    }
}