package com.zipsa.housing;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * 단지의 평형별 임대조건.
 *
 * <p>임대유형(housingType)이 단지가 아니라 여기 있는 이유 — 한 단지에 국민임대와
 * 영구임대가 함께 있는 경우가 실제로 107건 있다. 단지 쪽으로 올리면 그걸 잃는다.
 */
@Entity
@Table(name = "housing_complex_units")
public class HousingComplexUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complex_id", nullable = false)
    private HousingComplex complex;

    @Column(name = "external_id", nullable = false, length = 120)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "housing_type", nullable = false, length = 30)
    private HousingType housingType;

    @Column(name = "style_name", length = 50)
    private String styleName;

    @Column(name = "exclusive_area")
    private BigDecimal exclusiveArea;

    @Column(name = "supply_area")
    private BigDecimal supplyArea;

    /** 원 단위. 실거래가 테이블은 만원 단위라 섞지 않도록 주의. */
    private Long deposit;

    @Column(name = "monthly_rent")
    private Long monthlyRent;

    protected HousingComplexUnit() {
    }

    public Long getId() { return id; }
    public HousingComplex getComplex() { return complex; }
    public String getExternalId() { return externalId; }
    public HousingType getHousingType() { return housingType; }
    public String getStyleName() { return styleName; }
    public BigDecimal getExclusiveArea() { return exclusiveArea; }
    public BigDecimal getSupplyArea() { return supplyArea; }
    public Long getDeposit() { return deposit; }
    public Long getMonthlyRent() { return monthlyRent; }
}
