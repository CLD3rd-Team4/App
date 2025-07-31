package com.mapzip.auth.auth_service.service;

import com.mapzip.auth.auth_service.entity.AppUser;
import com.mapzip.auth.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public void registerNickname(String kakaoId, String nickname) {
        AppUser user = userRepository.findByKakaoId(Long.valueOf(kakaoId))
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        if (user.getNickname() != null) {
            throw new IllegalStateException("닉네임은 이미 등록되어 있습니다.");
        }

        if (userRepository.existsByNickname(nickname)) {
            throw new IllegalStateException("이미 사용 중인 닉네임입니다.");
        }

        user.setNickname(nickname);
        userRepository.save(user);
    }
}
