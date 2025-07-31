package com.mapzip.auth.auth_service.repository;

import com.mapzip.auth.auth_service.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByKakaoId(Long kakaoId);

    boolean existsByNickname(String nickname);

}