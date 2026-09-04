package com.zipsa.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import com.zipsa.common.BusinessException;
import com.zipsa.common.ErrorCode;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final Duration accessExpire;
    private final Duration refreshExpire;

    public JwtTokenProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessExpire = Duration.ofMinutes(properties.accessExpireMinutes());
        this.refreshExpire = Duration.ofDays(properties.refreshExpireDays());
    }

    public String createAccessToken(Long userId) {
        return build(userId, accessExpire);
    }

    public String createRefreshToken(Long userId) {
        return build(userId, refreshExpire);
    }

    private String build(Long userId, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                // jti 가 없으면 같은 초 안에 발급된 토큰이 바이트 단위로 동일해진다.
                // (iat/exp 는 초 단위라 로그인 직후 재발급 시 refresh_tokens.token unique 제약에 걸린다)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * 토큰에서 회원 ID 를 꺼낸다.
     * 만료와 위조를 구분해서 던져야 클라이언트가 "재발급"과 "재로그인"을 구분할 수 있다.
     */
    public Long parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    public long accessExpiresInSeconds() {
        return accessExpire.toSeconds();
    }

    public Instant refreshExpiresAt() {
        return Instant.now().plus(refreshExpire);
    }
}
