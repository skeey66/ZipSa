package com.zipsa.housing.dto;

import com.zipsa.housing.HousingComplexUnit;

/** 단지 상세 — 평형별 임대조건 한 줄. */
public record ComplexUnitResponse(
        Long id, String styleName, Double exclusiveArea,
        Long deposit, Long monthlyRent, Integer householdCount, String houseType
) {
    public static ComplexUnitResponse from(HousingComplexUnit u) {
        // 세대수·주택유형은 V21 부터 단지가 갖는다.
        // 화면으로 나가는 JSON 필드명은 그대로 유지해서 프론트가 영향받지 않게 한다.
        return new ComplexUnitResponse(u.getId(), u.getStyleName(),
                u.getExclusiveArea() == null ? null : u.getExclusiveArea().doubleValue(),
                u.getDeposit(), u.getMonthlyRent(),
                u.getComplex().getHouseholdCount(), u.getComplex().getHouseType());
    }
}
