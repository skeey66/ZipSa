package com.zipsa.policy;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 청년정책. 크롤러가 적재하므로 백엔드는 읽기만 한다. */
@Entity
@Table(name = "policies")
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicyCategory category;

    @Column(length = 50)
    private String region;

    @Column(length = 100)
    private String issuer;

    @Column(name = "target_job")
    private String targetJob;

    @Column(name = "target_age_range")
    private String targetAgeRange;

    @Column(name = "target_salary_range")
    private String targetSalaryRange;

    /** 회원 나이대와 겹치는지 비교하기 위한 숫자 범위. null 이면 조건 없음. */
    @Column(name = "target_min_age")
    private Integer targetMinAge;

    @Column(name = "target_max_age")
    private Integer targetMaxAge;

    @Column(name = "earn_min_amt")
    private Long earnMinAmt;

    @Column(name = "earn_max_amt")
    private Long earnMaxAmt;

    /** '제한없음|기혼|미혼' 형태의 원문. 신혼부부 정책 판별에 쓴다. */
    @Column(name = "marital_condition", length = 60)
    private String maritalCondition;

    /** 대상 시·도코드. "11,26" 또는 전국이면 "ALL". 지역 매칭의 기준. */
    @Column(name = "sido_codes", length = 120)
    private String sidoCodes;

    @Column(name = "apply_start_date")
    private LocalDate applyStartDate;

    @Column(name = "apply_end_date")
    private LocalDate applyEndDate;

    @Column(name = "apply_method", columnDefinition = "text")
    private String applyMethod;

    @Column(name = "source_name", length = 100)
    private String sourceName;

    @Column(name = "source_url", nullable = false, length = 500)
    private String sourceUrl;

    @Column(name = "crawled_at", nullable = false)
    private LocalDateTime crawledAt;

    protected Policy() {
    }

    /** 신청 가능 여부는 저장하지 않고 날짜로 계산한다. 저장하면 매일 갱신해야 한다. */
    public boolean isOpenOn(LocalDate today) {
        if (applyStartDate != null && today.isBefore(applyStartDate)) return false;
        return applyEndDate == null || !today.isAfter(applyEndDate);
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public PolicyCategory getCategory() { return category; }
    public String getRegion() { return region; }
    public String getIssuer() { return issuer; }
    public String getTargetJob() { return targetJob; }
    public String getTargetAgeRange() { return targetAgeRange; }
    public String getTargetSalaryRange() { return targetSalaryRange; }
    public Integer getTargetMinAge() { return targetMinAge; }
    public Integer getTargetMaxAge() { return targetMaxAge; }
    public Long getEarnMinAmt() { return earnMinAmt; }
    public Long getEarnMaxAmt() { return earnMaxAmt; }
    public String getMaritalCondition() { return maritalCondition; }
    public String getSidoCodes() { return sidoCodes; }
    public LocalDate getApplyStartDate() { return applyStartDate; }
    public LocalDate getApplyEndDate() { return applyEndDate; }
    public String getApplyMethod() { return applyMethod; }
    public String getSourceName() { return sourceName; }
    public String getSourceUrl() { return sourceUrl; }
}
