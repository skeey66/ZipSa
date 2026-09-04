package com.zipsa.housing.dto;

import com.zipsa.housing.PublicHousing;
import java.time.LocalDate;

/** HOUSING-001·002 — 모집 공고. */
public record NoticeResponse(
        Long id, String name, String housingType, String housingTypeName,
        String region, LocalDate recruitStartDate, LocalDate recruitEndDate,
        String status, long dDay, String applyUrl
) {
    public static NoticeResponse from(PublicHousing h, LocalDate today) {
        PublicHousing.RecruitStatus status = h.statusOn(today);
        // 마감까지 남은 일수. 이미 끝났으면 음수가 아니라 0 으로 보여준다.
        long dDay = Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(today, h.getRecruitEndDate()));
        return new NoticeResponse(h.getId(), h.getName(), h.getHousingType().name(),
                h.getHousingType().label(), h.getRegion(),
                h.getRecruitStartDate(), h.getRecruitEndDate(),
                status.name(), dDay, h.getApplyUrl());
    }
}
