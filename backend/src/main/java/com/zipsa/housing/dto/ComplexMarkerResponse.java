package com.zipsa.housing.dto;

import com.zipsa.housing.ComplexRepository.ComplexMarker;
import com.zipsa.housing.HousingType;

/**
 * 화면 10 지도 마커. 단지 단위로 묶은 값.
 * 금액은 원 단위(마이홈 API 원본). 화면에서 만원/억으로 포맷한다.
 */
public record ComplexMarkerResponse(
        Long complexNo, String name, String roadAddress, String institution,
        String housingType, String housingTypeName,
        Double latitude, Double longitude,
        Integer householdCount, Long styleCount,
        Long minDeposit, Long maxDeposit, Long minMonthlyRent, Long maxMonthlyRent,
        Double minArea, Double maxArea
) {
    public static ComplexMarkerResponse from(ComplexMarker m) {
        HousingType type = HousingType.valueOf(m.getHousingType());
        return new ComplexMarkerResponse(m.getComplexNo(), m.getName(), m.getRoadAddress(),
                m.getInstitution(), type.name(), type.label(),
                m.getLatitude(), m.getLongitude(), m.getHouseholdCount(), m.getStyleCount(),
                m.getMinDeposit(), m.getMaxDeposit(), m.getMinMonthlyRent(), m.getMaxMonthlyRent(),
                m.getMinArea(), m.getMaxArea());
    }
}
