package com.zipsa.mock.dto;

/**
 * [MOCK] AI 연동 상태.
 *
 * 프론트가 「AI」/「샘플」 배지를 어느 쪽으로 그릴지 미리 정하려고 씁니다.
 * 목업이므로 값은 항상 고정입니다. 실제 값이 필요해지면 {@code AiAvailability} 에 연결합니다.
 */
public record MockAiStatusResponse(boolean aiConfigured, String engine, String model, boolean mock) {

    public static MockAiStatusResponse notConfigured() {
        return new MockAiStatusResponse(false, "rule-based", null, true);
    }
}
