package com.zipsa.auth.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정. secret 은 .env 로만 주입하며 절대 커밋하지 않는다.
 * HS256 키는 최소 32바이트여야 하므로 짧은 값이 들어오면 기동 시점에 실패시킨다.
 */
@ConfigurationProperties(prefix = "zipsa.jwt")
public record JwtProperties(
        String secret,
        long accessExpireMinutes,
        long refreshExpireDays
) {
    public JwtProperties {
        if (secret == null || secret.getBytes().length < 32) {
            throw new IllegalStateException(
                    "zipsa.jwt.secret 이 32바이트 미만입니다. `openssl rand -base64 48` 로 생성해 .env 에 넣으세요.");
        }
    }
}
