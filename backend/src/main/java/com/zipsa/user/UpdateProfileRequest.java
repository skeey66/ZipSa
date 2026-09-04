package com.zipsa.user;

import jakarta.validation.constraints.Size;

/** 오퍼레이션 7. 전달된 필드만 변경한다. null 은 "변경하지 않음". */
public record UpdateProfileRequest(
        @Size(min = 2, max = 30, message = "닉네임은 2~30자여야 합니다.") String nickname,
        AgeRange ageRange,
        MaritalStatus maritalStatus,
        Job job,
        SalaryRange salaryRange,
        @Size(max = 30) String region
) {
}
