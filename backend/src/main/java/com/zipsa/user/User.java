package com.zipsa.user;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true, length = 50)
    private String loginId;

    /** BCrypt 해시. 평문은 어떤 경우에도 저장하지 않는다. */
    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, unique = true, length = 30)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_range", length = 20)
    private AgeRange ageRange;

    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status", length = 20)
    private MaritalStatus maritalStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Job job;

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_range", length = 20)
    private SalaryRange salaryRange;

    /** 거주 시·도. 지자체 정책 매칭에 쓴다. 정책 95%가 지역 한정이라 없으면 추천이 무의미해진다. */
    @Column(length = 30)
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    // 관리자 승격은 API 로 하지 않는다. DB 에서 직접 지정한다(권한 상승 경로 차단).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected User() {
        // JPA 전용
    }

    private User(String loginId, String password, String nickname,
                 AgeRange ageRange, MaritalStatus maritalStatus, Job job, SalaryRange salaryRange) {
        this.loginId = loginId;
        this.password = password;
        this.nickname = nickname;
        this.ageRange = ageRange;
        this.maritalStatus = maritalStatus;
        this.job = job;
        this.salaryRange = salaryRange;
        this.status = UserStatus.ACTIVE;
    }

    /** 회원가입. password 는 호출자가 이미 인코딩한 값을 넘긴다. */
    public static User signUp(String loginId, String encodedPassword, String nickname,
                              AgeRange ageRange, MaritalStatus maritalStatus,
                              Job job, SalaryRange salaryRange, String region) {
        User user = new User(loginId, encodedPassword, nickname, ageRange, maritalStatus,
                job, salaryRange);
        user.region = region;
        return user;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /** 전달된 값만 변경한다(PATCH 의미). null 은 "변경하지 않음". */
    public void updateProfile(String nickname, AgeRange ageRange, MaritalStatus maritalStatus,
                              Job job, SalaryRange salaryRange, String region) {
        if (nickname != null) this.nickname = nickname;
        if (ageRange != null) this.ageRange = ageRange;
        if (maritalStatus != null) this.maritalStatus = maritalStatus;
        if (job != null) this.job = job;
        if (salaryRange != null) this.salaryRange = salaryRange;
        if (region != null && !region.isBlank()) this.region = region;
    }

    /** 탈퇴. 작성한 글은 남기고 계정만 비활성화한다. */
    public void withdraw() {
        this.status = UserStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    /** 관리자가 계정을 정지시킨다. 작성한 글은 남기고 로그인만 막는다. */
    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    public void reactivate() {
        this.status = UserStatus.ACTIVE;
    }

    public boolean isAdmin() {
        return this.role == Role.ADMIN;
    }

    public String getRegion() { return region; }

    public Role getRole() {
        return role;
    }

    public Long getId() { return id; }
    public String getLoginId() { return loginId; }
    public String getPassword() { return password; }
    public String getNickname() { return nickname; }
    public AgeRange getAgeRange() { return ageRange; }
    public MaritalStatus getMaritalStatus() { return maritalStatus; }
    public Job getJob() { return job; }
    public SalaryRange getSalaryRange() { return salaryRange; }
    public UserStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
