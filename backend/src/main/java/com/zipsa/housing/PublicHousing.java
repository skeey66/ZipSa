package com.zipsa.housing;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 모집 공고 (LH 분양임대공고문). 화면 10 캘린더의 점은 recruit_start_date 기준. */
@Entity
@Table(name = "public_housings")
public class PublicHousing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "housing_type", nullable = false, length = 30)
    private HousingType housingType;

    @Column(length = 50)
    private String region;

    @Column(name = "recruit_start_date", nullable = false)
    private LocalDate recruitStartDate;

    @Column(name = "recruit_end_date", nullable = false)
    private LocalDate recruitEndDate;

    @Column(name = "apply_url", length = 500)
    private String applyUrl;

    @Column(name = "source_url", nullable = false, length = 500)
    private String sourceUrl;

    @Column(name = "crawled_at", nullable = false)
    private LocalDateTime crawledAt;

    protected PublicHousing() {
    }

    /** 모집 상태는 저장하지 않고 날짜로 계산한다. 저장하면 매일 갱신해줘야 한다. */
    public RecruitStatus statusOn(LocalDate today) {
        if (today.isBefore(recruitStartDate)) return RecruitStatus.UPCOMING;
        if (today.isAfter(recruitEndDate)) return RecruitStatus.CLOSED;
        return RecruitStatus.OPEN;
    }

    public enum RecruitStatus { UPCOMING, OPEN, CLOSED }

    public Long getId() { return id; }
    public String getName() { return name; }
    public HousingType getHousingType() { return housingType; }
    public String getRegion() { return region; }
    public LocalDate getRecruitStartDate() { return recruitStartDate; }
    public LocalDate getRecruitEndDate() { return recruitEndDate; }
    public String getApplyUrl() { return applyUrl; }
    public String getSourceUrl() { return sourceUrl; }
}
