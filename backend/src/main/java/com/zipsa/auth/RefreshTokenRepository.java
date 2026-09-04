package com.zipsa.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    void deleteByToken(String token);

    /** 로그아웃·탈퇴 시 해당 회원의 모든 세션을 끊는다. */
    void deleteByUserId(Long userId);
}
