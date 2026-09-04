package com.zipsa.transaction;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 아파트 실거래 한 건. 크롤러가 직접 INSERT 하므로 백엔드는 읽기만 한다.
 * 금액 단위는 API 원본 그대로 "만원" 이다.
 *
 * <p>단지명·좌표·건축년도는 {@link Apartment} 가 갖는다. 거래마다 달라지는 사실만 여기 남긴다.
 */
@Entity
@Table(name = "real_estate_transactions")
public class RealEstateTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 목록 한 페이지가 50건이라 LAZY 로 두면 단지명을 꺼낼 때마다 쿼리가 한 번씩 더 나간다.
    // 조회 쪽은 전부 @EntityGraph 로 함께 가져오므로 LAZY 를 유지하고 페치 계획을 명시한다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "apartment_id", nullable = false)
    private Apartment apartment;

    /** 만원. 매매는 거래금액, 전월세는 보증금. */
    @Column(name = "deal_amount", nullable = false)
    private Long dealAmount;

    /** 만원. 매매는 null, 전세는 0, 월세는 월세액. */
    @Column(name = "monthly_rent")
    private Long monthlyRent;

    @Column(name = "exclusive_area", nullable = false)
    private BigDecimal exclusiveArea;

    @Column(name = "floor")
    private Integer floor;

    @Column(name = "deal_date", nullable = false)
    private LocalDate dealDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "deal_type", nullable = false, length = 20)
    private DealType dealType;

    protected RealEstateTransaction() {
    }

    public Long getId() { return id; }
    public Apartment getApartment() { return apartment; }
    public Long getDealAmount() { return dealAmount; }
    public Long getMonthlyRent() { return monthlyRent; }
    public BigDecimal getExclusiveArea() { return exclusiveArea; }
    public Integer getFloor() { return floor; }
    public LocalDate getDealDate() { return dealDate; }
    public DealType getDealType() { return dealType; }
}
