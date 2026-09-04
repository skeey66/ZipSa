package com.zipsa.policy.dto;

import com.zipsa.policy.Policy;
import java.time.LocalDate;

/** POLICY-001 목록 · POLICY-002 상세 공용. 상세에서만 content/applyMethod 를 채운다. */
public record PolicyResponse(
        Long policyId, String title, String summary, String content,
        String category, String categoryName,
        String region, String issuer,
        String targetAgeRange, String targetJob, String targetSalaryRange,
        LocalDate applyStartDate, LocalDate applyEndDate,
        boolean open, Long dDay,
        String applyMethod, String sourceName, String sourceUrl
) {
    private static final int SUMMARY_LENGTH = 90;

    public static PolicyResponse listItem(Policy p, LocalDate today) {
        return of(p, today, false);
    }

    public static PolicyResponse detail(Policy p, LocalDate today) {
        return of(p, today, true);
    }

    private static PolicyResponse of(Policy p, LocalDate today, boolean full) {
        Long dDay = (p.getApplyEndDate() == null) ? null
                : Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(today, p.getApplyEndDate()));
        return new PolicyResponse(
                p.getId(), p.getTitle(), summarize(p.getContent()),
                full ? p.getContent() : null,
                p.getCategory().name(), p.getCategory().label(),
                p.getRegion(), p.getIssuer(),
                p.getTargetAgeRange(), p.getTargetJob(), p.getTargetSalaryRange(),
                p.getApplyStartDate(), p.getApplyEndDate(),
                p.isOpenOn(today), dDay,
                full ? p.getApplyMethod() : null,
                p.getSourceName(), p.getSourceUrl());
    }

    /** 목록에 본문을 통째로 내려보내면 응답이 수백 KB 가 된다. 앞부분만 자른다. */
    private static String summarize(String content) {
        if (content == null || content.isBlank()) return null;
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() <= SUMMARY_LENGTH ? flat : flat.substring(0, SUMMARY_LENGTH) + "…";
    }
}
