package com.zipsa.user;

import java.time.LocalDateTime;
import java.util.List;

/** 오퍼레이션 6·7. 마이페이지 「내 정보」 탭. */
public record UserProfileResponse(
        Long userId,
        String loginId,
        String nickname,
        AgeRange ageRange,
        MaritalStatus maritalStatus,
        Job job,
        SalaryRange salaryRange,
        String region,
        UserStatus status,
        Role role,
        LocalDateTime createdAt,
        List<RecentActivity> recentActivities
) {
    /** 마이페이지 「최근 활동」 목록의 한 줄. */
    public record RecentActivity(String type, String title, LocalDateTime occurredAt) {
    }

    public static UserProfileResponse of(User user, List<RecentActivity> activities) {
        return new UserProfileResponse(
                user.getId(), user.getLoginId(), user.getNickname(),
                user.getAgeRange(), user.getMaritalStatus(), user.getJob(), user.getSalaryRange(),
                user.getRegion(),
                user.getStatus(), user.getRole(), user.getCreatedAt(), activities);
    }
}
