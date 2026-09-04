package com.zipsa.loan.dto;

import jakarta.validation.constraints.*;

/** LOAN-005 — 내 대출 결과 등록. */
public record LoanRecordRequest(

        @NotBlank(message = "은행을 선택해 주세요.")
        String bankName,

        // 반려면 금액이 없다. 그래서 @NotNull 을 걸 수 없고 서비스에서 조합을 검사한다.
        @Min(value = 10_000_000, message = "1천만원 이상 입력해 주세요.")
        @Max(value = 1_000_000_000, message = "10억원 이하로 입력해 주세요.")
        Long actualLimit,

        @DecimalMin(value = "0.1", message = "금리는 0.1% 이상이어야 합니다.")
        @DecimalMax(value = "20.0", message = "금리는 20% 이하여야 합니다.")
        Double actualRate,

        Long policyId,

        /** true 면 반려. 승인 금액만 모으면 "떨어질 수도 있다" 를 알 수 없다. */
        boolean rejected
) {
}
