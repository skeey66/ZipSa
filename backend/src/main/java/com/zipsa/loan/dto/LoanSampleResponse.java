package com.zipsa.loan.dto;

import java.util.List;

/**
 * 화면 14 — 막대 클릭 시 나오는 「이 금액대를 받은 사람들」.
 *
 * 채용 사이트의 합격자 스펙처럼, 나와 비슷한 조건의 사람이 실제로 얼마를 받았는지 보여준다.
 * 개인을 특정할 수 있는 값(닉네임·아이디)은 담지 않는다.
 */
public record LoanSampleResponse(
        String bankName,
        String bucketLabel,
        // 반려 구간이면 화면 문구와 표가 달라진다("승인 한도 0원" 은 거짓말이다).
        boolean rejectedBucket,
        long total,
        String summary,          // "이 구간은 연소득 3~4천만원 직장인이 가장 많습니다"
        List<Sample> samples,
        Mine mine                // 내 조건이 이 구간에 속하는지
) {
    public record Sample(
            String ageRange, String job, String salaryRange, String region,
            long actualLimit, double actualRate, boolean similar   // 나와 조건이 겹치면 강조
    ) {
    }

    public record Mine(boolean inThisBucket, long expectedLimit, String message) {
    }
}
