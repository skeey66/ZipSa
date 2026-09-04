package com.zipsa.housing;

/**
 * 공급 유형. 두 API 가 모두 한글 문자열로 주기 때문에 크롤러에서 코드로 변환한다.
 * 매핑에 없는 값은 OTHER 로 두되, 원문 유형명은 잃지 않도록 화면에는 코드→한글로 되돌린다.
 */
public enum HousingType {
    HAPPY_HOUSE("행복주택"),
    NATIONAL_RENTAL("국민임대"),
    PERMANENT_RENTAL("영구임대"),
    PURCHASE_RENTAL("매입임대"),
    JEONSE_RENTAL("전세임대"),
    INTEGRATED_RENTAL("통합공공임대"),
    PUBLIC_RENTAL("공공임대"),
    LONG_JEONSE("장기전세"),
    PUBLIC_SUPPORT_PRIVATE("공공지원민간임대"),
    OTHER("기타");

    private final String label;

    HousingType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
