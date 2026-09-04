package com.zipsa.transaction.dto;

import com.zipsa.transaction.RealEstateTransaction;
import java.time.LocalDate;

/** 오퍼레이션 38 — 실거래 목록 (화면 11 하단 / 마커 클릭 시) */
public record TransactionResponse(
        Long id,
        String aptName,
        String dealType,
        Long dealAmount,
        Long monthlyRent,
        Double exclusiveArea,
        Integer floor,
        Integer buildYear,
        LocalDate dealDate
) {
    public static TransactionResponse from(RealEstateTransaction t) {
        // 단지명·건축년도는 V20 부터 apartments 가 갖는다.
        // 화면으로 나가는 JSON 필드명은 그대로 유지해서 프론트가 영향받지 않게 한다.
        return new TransactionResponse(
                t.getId(), t.getApartment().getName(), t.getDealType().name(),
                t.getDealAmount(), t.getMonthlyRent(),
                t.getExclusiveArea() == null ? null : t.getExclusiveArea().doubleValue(),
                t.getFloor(), t.getApartment().getBuildYear(), t.getDealDate());
    }
}
