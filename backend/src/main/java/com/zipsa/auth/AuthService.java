package com.zipsa.auth;

import com.zipsa.auth.dto.*;
import com.zipsa.auth.jwt.JwtTokenProvider;
import com.zipsa.common.BusinessException;
import com.zipsa.common.ErrorCode;
import com.zipsa.user.User;
import com.zipsa.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@Transactional(readOnly = true)
public class AuthService {

    /** BCrypt 는 72바이트를 넘는 입력을 잘라낸다. 잘린 뒤가 무시되면 서로 다른 비밀번호가 같아진다. */
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    /** 오퍼레이션 1 */
    @Transactional
    public SignupResponse signUp(SignupRequest request) {
        if (userRepository.existsByLoginId(request.loginId())) {
            throw new BusinessException(ErrorCode.USER_ID_DUPLICATED);
        }
        if (userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }
        validatePasswordLength(request.password());

        User user = User.signUp(
                request.loginId(),
                passwordEncoder.encode(request.password()),
                request.nickname(),
                request.ageRange(),
                request.maritalStatus(),
                request.job(),
                request.salaryRange(),
                request.region());

        return SignupResponse.from(userRepository.save(user));
    }

    /** 오퍼레이션 2 */
    public CheckIdResponse checkLoginId(String loginId) {
        return CheckIdResponse.of(!userRepository.existsByLoginId(loginId));
    }

    /** 오퍼레이션 3 */
    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        // 탈퇴한 계정도 "아이디가 없다"가 아니라 같은 오류로 응답한다. 계정 존재 여부를 흘리지 않기 위해서다.
        // 순서가 중요하다. 상태를 먼저 보면 "정지된 계정입니다" 응답이
        // 비밀번호를 모르는 사람에게도 계정 존재를 알려준다(계정 열거 취약점).
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() == com.zipsa.user.UserStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
        }
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        return issueTokens(user.getId(), user.getNickname(), user.getRole());
    }

    /** 오퍼레이션 4. 기존 Refresh Token 은 폐기하고 새 쌍을 발급한다(회전). */
    @Transactional
    public TokenResponse reissue(String refreshTokenValue) {
        Long userId = tokenProvider.parseUserId(refreshTokenValue);

        RefreshToken stored = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        if (stored.isExpired()) {
            refreshTokenRepository.delete(stored);
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        // Hibernate 는 기본적으로 insert 를 delete 보다 먼저 내보낸다.
        // 새 토큰을 넣기 전에 이전 토큰이 확실히 지워지도록 여기서 flush 한다.
        refreshTokenRepository.delete(stored);
        refreshTokenRepository.flush();

        TokenResponse issued = issueTokens(userId, null, null);
        return TokenResponse.ofReissue(issued.accessToken(), issued.refreshToken(), issued.accessTokenExpiresIn());
    }

    /** 오퍼레이션 5 */
    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    /** 오퍼레이션 8 */
    @Transactional
    public void withdraw(Long userId, String rawPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.withdraw();
        refreshTokenRepository.deleteByUserId(userId);
    }

    private TokenResponse issueTokens(Long userId, String nickname, com.zipsa.user.Role role) {
        String access = tokenProvider.createAccessToken(userId);
        String refresh = tokenProvider.createRefreshToken(userId);

        LocalDateTime expiresAt = LocalDateTime.ofInstant(
                tokenProvider.refreshExpiresAt(), ZoneId.systemDefault());
        refreshTokenRepository.save(new RefreshToken(userId, refresh, expiresAt));

        return TokenResponse.ofLogin(access, refresh, tokenProvider.accessExpiresInSeconds(), nickname, role);
    }

    private void validatePasswordLength(String rawPassword) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "비밀번호가 너무 깁니다. 한글은 한 글자가 3바이트이며, 전체 72바이트를 넘을 수 없습니다.");
        }
    }
}
