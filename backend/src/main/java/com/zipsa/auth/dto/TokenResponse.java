package com.zipsa.auth.dto;

import com.zipsa.user.Role;

/**
 * 오퍼레이션 3·4의 응답 (명세 v4 §3.3, §3.4).
 * nickname 과 role 은 헤더 표시용이라 재발급(4) 시에는 null 이다.
 *
 * ⚠️ role 은 화면 표시(관리자 메뉴 노출) 용도일 뿐이다.
 *    실제 권한 판정은 항상 서버가 토큰으로 한다. 클라이언트 값을 신뢰하지 않는다.
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresIn,
        String nickname,
        Role role
) {
    public static TokenResponse ofLogin(String access, String refresh, long expiresIn,
                                        String nickname, Role role) {
        return new TokenResponse(access, refresh, expiresIn, nickname, role);
    }

    public static TokenResponse ofReissue(String access, String refresh, long expiresIn) {
        return new TokenResponse(access, refresh, expiresIn, null, null);
    }
}
