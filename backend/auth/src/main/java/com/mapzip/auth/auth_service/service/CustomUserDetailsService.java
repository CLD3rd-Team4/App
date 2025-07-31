package com.mapzip.auth.auth_service.service;

import com.mapzip.auth.auth_service.entity.AppUser;
import com.mapzip.auth.auth_service.principal.UserPrincipal;
import com.mapzip.auth.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.security.core.userdetails.UserDetailsService;


@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String kakaoIdString) throws UsernameNotFoundException {
        Long kakaoId;
        try {
            kakaoId = Long.parseLong(kakaoIdString);
        } catch (NumberFormatException e) {
            throw new UsernameNotFoundException("잘못된 사용자 ID 형식: " + kakaoIdString);
        }

        AppUser appUser = userRepository.findByKakaoId(kakaoId)
                .orElseThrow(() -> new UsernameNotFoundException("사용자 없음: " + kakaoId));

        return new UserPrincipal(appUser);
    }


}

