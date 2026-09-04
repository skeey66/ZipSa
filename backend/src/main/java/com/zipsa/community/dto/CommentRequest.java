package com.zipsa.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(
        @NotBlank(message = "댓글을 입력하세요.") @Size(max = 1000) String content
) {
}
