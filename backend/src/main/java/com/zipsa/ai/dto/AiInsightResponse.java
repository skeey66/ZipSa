package com.zipsa.ai.dto;

import java.util.List;

/**
 * 정책·뉴스 상세의 「AI 요약」과 「나에게 어떻게 적용되나」.
 *
 * aiGenerated=false 는 "이 글을 LLM 이 쓰지 않았다" 는 뜻이다.
 * 화면은 이 값으로 「샘플」 배지를 붙인다. 목업을 진짜처럼 보이게 두면 시연에서 오해가 생긴다.
 */
public record AiInsightResponse(
        List<String> summary,        // 3줄 요약
        Application application,     // 내 상황 적용
        boolean aiGenerated
) {
    public record Application(
            String verdict,          // 한 줄 결론
            List<String> reasons,    // 근거
            List<String> nextSteps,  // 지금 할 일
            String tone              // good | check | caution — 화면 색 결정
    ) {
    }
}
