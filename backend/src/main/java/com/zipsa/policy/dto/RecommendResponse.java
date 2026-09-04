package com.zipsa.policy.dto;

import java.util.List;

/**
 * POLICY-004 맞춤 정책.
 * relevanceRate 는 규칙 기반 점수다. LLM 을 쓰지 않으므로 같은 프로필이면 항상 같은 결과가 나온다.
 * matchReasons 는 왜 추천됐는지 사용자에게 그대로 보여주기 위한 것이다.
 */
public record RecommendResponse(
        Long policyId, String title, String categoryName, String region,
        Long dDay, int relevanceRate, List<String> matchReasons
) {
}
