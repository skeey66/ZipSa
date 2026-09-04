package com.zipsa.policy;

/** 정책 분류. 크롤러가 제목·본문 키워드로 판정해 저장한다. */
public enum PolicyCategory {
    HOUSING("주거지원"),
    LOAN("대출·이자"),
    PUBLIC_HOUSING("공공임대"),
    SUPPLY("주택공급");

    private final String label;

    PolicyCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
