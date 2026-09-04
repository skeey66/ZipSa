package com.zipsa.auth.dto;

import com.zipsa.user.User;

import java.time.LocalDateTime;

public record SignupResponse(Long userId, String loginId, String nickname, LocalDateTime createdAt) {
    public static SignupResponse from(User user) {
        return new SignupResponse(user.getId(), user.getLoginId(), user.getNickname(), user.getCreatedAt());
    }
}
