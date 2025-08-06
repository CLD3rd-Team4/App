package com.mapzip.auth.auth_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()  // Health check 허용
                .anyRequest().authenticated()  // 나머지는 인증 필요
            )
            .csrf(csrf -> csrf.disable())  // CSRF 비활성화 (API 서버이므로)
            .httpBasic(httpBasic -> httpBasic.disable())  // Basic Auth 비활성화
            .formLogin(formLogin -> formLogin.disable());  // Form Login 비활성화

        return http.build();
    }
}
