package com.zipsa.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** 오퍼레이션 8. 본인 확인용으로 비밀번호를 다시 받는다. */
public record WithdrawRequest(@NotBlank(message = "비밀번호를 입력해 주세요.") String password) {
}
