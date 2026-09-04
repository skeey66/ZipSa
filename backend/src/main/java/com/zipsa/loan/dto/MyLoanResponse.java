package com.zipsa.loan.dto;

import java.util.List;

/** LOAN-002 — 내가 등록한 대출 목록. 커뮤니티 뱃지의 원천이기도 하다. */
public record MyLoanResponse(List<Item> loans) {
    public record Item(
            Long id, String bankName, String bankCode, boolean rejected,
            Long actualLimit, Double actualRate, String createdAt
    ) {
    }
}
