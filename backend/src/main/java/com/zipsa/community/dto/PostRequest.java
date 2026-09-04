package com.zipsa.community.dto;

import com.zipsa.community.PostCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PostRequest(
        @NotBlank(message = "제목을 입력하세요.") @Size(max = 200) String title,
        @NotBlank(message = "내용을 입력하세요.") String content,
        @NotNull(message = "카테고리를 선택하세요.") PostCategory category
) {
}
