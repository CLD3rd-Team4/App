package com.mapzip.auth.auth_service.config;

import com.mapzip.auth.auth_service.config.util.Jwks;
import com.mapzip.auth.auth_service.service.CustomUserDetailsService;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.jwk.RSAKey;
import java.util.UUID;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    @Bean
    // Spring Security 필터 체인을 정의 (AuthorizationServer용 기본 보안 설정 적용)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        return http.build();
    }

    @Bean
    // 클라이언트 등록 정보 저장소 (client_id, secret, redirect_uri 등 정보 설정)
    // OAuth2 클라이언트 등록을 메모리상(Inmemory)에 저장하는 역할
    // DB 기반으로 변경 필요
    public RegisteredClientRepository registeredClientRepository() {
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString()) // 클라이언트 내부 식별자
                .clientId("gateway") // 인증 서버에 인증 요청 보낼 때 사용하는 이름
                .clientSecret("{noop}secret") // 인증서버에 접근할 수 있는 자격 비빌번호, {noop}은 암호화되지 않았다는 의미
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE) // 사용자 로그인을 통해 인가 코드 받고, 그걸로 토큰 요청
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN) // Access Token 만료 시 새로 받을 수 있는 토큰
                .redirectUri("http://gateway.mapzip.dev/login/oauth2/code/gateway") // 인가 코드 발급 후 인증 서버가 사용자를 리디렉션할 URI
                .scope(OidcScopes.OPENID) // OAuth2/OIDC 범위 설정
                .scope("profile") // openid: OIDC 인증(필수), profile: 사용자 이름, 프로필 등 추가 정보 요청 가능
                .build();

        return new InMemoryRegisteredClientRepository(client); // 서버 재시작하면 사라짐(메모리 저장소), DB로 변경해야함
    }

    @Bean
    // JWT 서명을 위한 RSA 키를 생성하고 JWK 형식으로 제공
    public JWKSource<SecurityContext> jwkSource() {
        RSAKey rsaKey = Jwks.generateRsa();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return (selector, context) -> selector.select(jwkSet);
    }

    @Bean
    // 인증 서버 메타데이터 정보 설정 (issuer URL 등)
    // issuer란? OAuth2 또는 OIDC에서 토큰을 발급한 주체(issuer)를 식별하는 고유 식별자
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer("http://auth.mapzip.dev") // 클라이언트(Spring Gateway)는 이 iss값이 일치해야 신뢰함
                .build();
    }

    @Bean
    // 사용자 인증을 위한 AuthenticationManager 빈 등록
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        authBuilder
                .userDetailsService(userDetailsService)
                .passwordEncoder(passwordEncoder());
        return authBuilder.build();
    }

    @Bean
    // 비밀번호 인코더 (BCrypt 사용)
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
