package com.zipsa.transaction.dto;

import com.zipsa.transaction.Region;

/**
 * 오퍼레이션 36 — 지역 목록 (화면 11 좌측 필터)
 *
 * <p>hasData 는 그 지역에 수집된 실거래가 있는지다. 크롤러가 아직 돌지 않은 지역이
 * 다수라, 이 값 없이 목록만 주면 화면이 눌러도 빈 지도만 나오는 칩을 잔뜩 그린다.
 */
public record RegionResponse(String regionCode, String regionName, String sido, String sigungu,
                             boolean hasData) {
    public static RegionResponse from(Region r, boolean hasData) {
        return new RegionResponse(r.getRegionCode(), r.getRegionName(), r.getSido(), r.getSigungu(),
                hasData);
    }
}
