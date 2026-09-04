package com.zipsa.housing;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * 단지 (마이홈포털 단지정보) — 평형과 무관한 사실만 갖는다.
 *
 * <p>V21 이전에는 「단지 × 평형」이 한 행이라 6,993개 단지가 이름·주소·좌표를
 * 48,886행에 걸쳐 반복했다. 그 결과 총 세대수가 평형마다 어긋난 단지가 109개 생겼다.
 */
@Entity
@Table(name = "housing_complexes")
public class HousingComplex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complex_no", nullable = false)
    private Long complexNo;

    @Column(nullable = false)
    private String name;

    private String institution;

    /** 법정동코드 5자리. 시도·시군구 이름은 regions 에서 조인해 얻는다. */
    @Column(name = "region_code", nullable = false, length = 10)
    private String regionCode;

    @Column(name = "road_address")
    private String roadAddress;

    @Column(name = "house_type", length = 30)
    private String houseType;

    @Column(name = "household_count")
    private Integer householdCount;

    @Column(name = "parking_count")
    private Integer parkingCount;

    @Column(name = "completed_date", length = 8)
    private String completedDate;

    private BigDecimal latitude;
    private BigDecimal longitude;

    protected HousingComplex() {
    }

    public Long getId() { return id; }
    public Long getComplexNo() { return complexNo; }
    public String getName() { return name; }
    public String getInstitution() { return institution; }
    public String getRegionCode() { return regionCode; }
    public String getRoadAddress() { return roadAddress; }
    public String getHouseType() { return houseType; }
    public Integer getHouseholdCount() { return householdCount; }
    public Integer getParkingCount() { return parkingCount; }
    public String getCompletedDate() { return completedDate; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
}
