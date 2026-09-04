package com.zipsa.auth.dto;

import com.zipsa.user.*;
import jakarta.validation.constraints.*;

/** 오퍼레이션 1. 온보딩 6단계(와이어프레임 03·04)에서 모은 값을 한 번에 받는다. */
public record SignupRequest(

        @NotBlank(message = "아이디를 입력해 주세요.")
        @Pattern(regexp = "^[A-Za-z0-9_]{4,50}$", message = "아이디는 영문·숫자·언더스코어 4~50자여야 합니다.")
        String loginId,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(min = 8, max = 72, message = "비밀번호는 8자 이상 72자 이하여야 합니다.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message = "비밀번호는 영문·숫자·특수문자를 모두 포함해야 합니다.")
        String password,

        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(min = 2, max = 30, message = "닉네임은 2~30자여야 합니다.")
        String nickname,

        @NotNull(message = "나이대를 선택해 주세요.")
        AgeRange ageRange,

        @NotNull(message = "결혼 여부를 선택해 주세요.")
        MaritalStatus maritalStatus,

        @NotNull(message = "직업을 선택해 주세요.")
        Job job,

        @NotNull(message = "연소득 구간을 선택해 주세요.")
        SalaryRange salaryRange,

        // 지자체 정책이 전체의 95% 라 거주지가 없으면 맞춤 추천이 성립하지 않는다.
        @NotBlank(message = "거주 지역을 선택해 주세요.")
        @Size(max = 30)
        String region,

        @AssertTrue(message = "개인정보 수집 및 이용에 동의해야 가입할 수 있습니다.")
        boolean agreePrivacy,

        @AssertTrue(message = "커뮤니티 내 활동정보 활용에 동의해야 가입할 수 있습니다.")
        boolean agreeCommunity,

        boolean agreeMarketing
) {
}
