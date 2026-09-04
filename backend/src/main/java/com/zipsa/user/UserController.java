package com.zipsa.user;

import com.zipsa.auth.AuthService;
import com.zipsa.auth.dto.WithdrawRequest;
import com.zipsa.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    public UserController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    /** 오퍼레이션 6 — 내 정보 조회 */
    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getMe(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(userService.getMyProfile(userId));
    }

    /** 오퍼레이션 7 — 내 정보 수정 */
    @PatchMapping("/me")
    public ApiResponse<UserProfileResponse> updateMe(@AuthenticationPrincipal Long userId,
                                                     @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(userService.updateMyProfile(userId, request));
    }

    /** 오퍼레이션 8 — 회원 탈퇴 */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal Long userId,
                         @Valid @RequestBody WithdrawRequest request) {
        authService.withdraw(userId, request.password());
    }
}
