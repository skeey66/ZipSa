package com.zipsa.loan.dto;

import java.util.List;

/**
 * 화면 14 「나의 대출 예측하기」.
 *
 * profile  — 화면 상단 「내 정보」. 어떤 조건으로 계산했는지 보여줘야 숫자를 믿을 수 있다.
 * buckets  — 막대그래프의 x축 구간 라벨 (5칸)
 * banks    — 은행별 막대 높이와 강조 구간
 * report   — 화면 하단 「분석레포트」
 */
public record LoanPredictionResponse(
        MyProfile profile,
        List<String> buckets,
        List<BankPrediction> banks,
        AnalysisReport report
) {
    /** 화면 상단에 그대로 보여줄 내 조건. */
    public record MyProfile(
            String nickname, String ageRange, String job, String salaryRange, String maritalStatus
    ) {
    }

    /** 은행 카드 하나. */
    public record BankPrediction(
            String bankName,
            String theme,             // 카드 색 키 (프론트가 팔레트로 매핑)
            List<Long> distribution,  // 구간별 표본 수 = 막대 높이
            int highlightIndex,       // 가장 가능성 높은 구간 (①)
            long expectedLimit,       // 내 조건 기준 예상 한도(원)
            double expectedRate,      // 예상 금리(%)
            long sampleSize,
            String note               // 팝업(14b)의 한 줄 설명
    ) {
    }

    /**
     * 분석레포트.
     * ⚠️ AI 가 쓴 것처럼 보이지만 지금은 규칙 기반으로 만든 목업이다.
     *    OPENAI_API_KEY 가 연결되면 이 자리를 실제 LLM 응답으로 교체한다.
     */
    public record AnalysisReport(
            String headline,             // 요약 한 줄
            String scope,                // 분석 대상·표본·기준일
            List<Metric> metrics,        // 상단 지표 카드
            List<Section> sections,      // 본문
            String recommendedBank,
            List<String> limitations,    // 이 수치를 어디까지 믿을 수 있는지
            String disclaimer,
            boolean aiGenerated          // false = 규칙 기반. 화면에 그대로 표시해 오해를 막는다
    ) {
        /** 상단에 큰 숫자로 세우는 지표. */
        public record Metric(String label, String value, String note) {
        }

        /** 본문 한 절. */
        public record Section(String title, List<String> body) {
        }
    }
}
