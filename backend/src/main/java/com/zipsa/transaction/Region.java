package com.zipsa.transaction;

import jakarta.persistence.*;

/** 법정동코드(시군구). 화면 11 좌측 「지역」 필터의 선택지. */
@Entity
@Table(name = "regions")
public class Region {

    @Id
    @Column(name = "region_code", length = 10)
    private String regionCode;

    @Column(name = "region_name", nullable = false, length = 100)
    private String regionName;

    @Column(nullable = false, length = 30)
    private String sido;

    @Column(length = 30)
    private String sigungu;

    protected Region() {
    }

    public String getRegionCode() { return regionCode; }
    public String getRegionName() { return regionName; }
    public String getSido() { return sido; }
    public String getSigungu() { return sigungu; }
}
