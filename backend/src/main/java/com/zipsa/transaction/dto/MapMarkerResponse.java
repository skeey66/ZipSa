package com.zipsa.transaction.dto;

import com.zipsa.transaction.TransactionRepository.MapMarker;
import java.time.LocalDate;

/**
 * 오퍼레이션 37 — 지도 마커 (화면 11)
 * 금액은 만원 단위. 화면에서 억/만원으로 포맷한다.
 */
public record MapMarkerResponse(
        String aptName,
        Double latitude,
        Double longitude,
        Long dealCount,
        Long avgAmount,
        Long minAmount,
        Long maxAmount,
        Double avgArea,
        LocalDate lastDealDate
) {
    public static MapMarkerResponse from(MapMarker m) {
        return new MapMarkerResponse(m.getAptName(), m.getLatitude(), m.getLongitude(),
                m.getDealCount(), m.getAvgAmount(), m.getMinAmount(), m.getMaxAmount(),
                m.getAvgArea(), m.getLastDealDate());
    }
}
