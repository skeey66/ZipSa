package com.zipsa.transaction;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * 단지. 좌표·건축년도처럼 「거래」가 아니라 「단지」에 딸린 사실을 보관한다.
 *
 * <p>V20 이전에는 이 값들이 거래 행마다 복제돼 있었다. 같은 단지의 거래가 평균 7.4건이라
 * 좌표가 그만큼 중복됐고, 건축년도가 행마다 어긋나는 단지가 95개 생겼다.
 */
@Entity
@Table(name = "apartments")
public class Apartment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "region_code", nullable = false, length = 10)
    private String regionCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "build_year")
    private Integer buildYear;

    private BigDecimal latitude;
    private BigDecimal longitude;

    protected Apartment() {
    }

    public Long getId() { return id; }
    public String getRegionCode() { return regionCode; }
    public String getName() { return name; }
    public Integer getBuildYear() { return buildYear; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
}
