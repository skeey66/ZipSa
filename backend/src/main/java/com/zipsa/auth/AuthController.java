package com.zipsa.auth;

import com.zipsa.auth.dto.*;
import com.zipsa.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 오퍼레이션 1 — 회원가입 */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signUp(@Valid @RequestBody SignupRequest request) {
        SignupResponse payload = authService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(payload));
    }

    /** 오퍼레이션 2 — 아이디 중복확인 */
    @GetMapping("/check-id")
    public ApiResponse<CheckIdResponse> checkId(@RequestParam @NotBlank String loginId) {
        return ApiResponse.ok(authService.checkLoginId(loginId));
    }

    /** 오퍼레이션 3 — 로그인 */
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    /** 오퍼레이션 4 — 토큰 재발급 */
    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> reissue(@Valid @RequestBody ReissueRequest request) {
        return ApiResponse.ok(authService.reissue(request.refreshToken()));
    }

    /** 오퍼레이션 5 — 로그아웃 */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal Long userId) {
        authService.logout(userId);
    }
}
