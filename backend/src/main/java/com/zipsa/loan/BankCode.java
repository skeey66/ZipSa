package com.zipsa.loan;

import java.util.Arrays;
import java.util.Optional;

/**
 * 지원 은행. 커뮤니티 뱃지 이미지 파일명(/badges/{code}.png)과 코드가 1:1로 맞는다.
 *
 * 은행명을 문자열로 흘리면 "KB국민은행" / "국민은행" / "KB" 가 섞여서
 * 뱃지가 안 붙거나 통계가 갈라진다. 입력 시점에 enum 으로 고정한다.
 */
public enum BankCode {
    KB("KB국민은행"),
    WOORI("우리은행"),
    NH("NH농협은행"),
    HANA("하나은행");

    private final String displayName;

    BankCode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** 저장된 은행명(한글)에서 코드를 되찾는다. 기존 목업 데이터가 한글로 들어있다. */
    public static Optional<BankCode> fromDisplayName(String name) {
        if (name == null) return Optional.empty();
        return Arrays.stream(values())
                .filter(b -> b.displayName.equals(name.trim()))
                .findFirst();
    }
}
