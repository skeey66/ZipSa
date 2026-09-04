package com.zipsa.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** 오퍼레이션 4. */
public record ReissueRequest(@NotBlank String refreshToken) {
}
