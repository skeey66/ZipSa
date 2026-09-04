package com.zipsa.loan;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 회원이 실제로 승인받은 대출. 화면 14 그래프의 표본이다.
 *
 * ⚠️ 지금 들어있는 값은 목업이다(scripts/seed_loan_data.py).
 *    실제 사용자가 등록하는 화면은 아직 와이어프레임에 없다.
 */
@Entity
@Table(name = "loan_actuals")
public class LoanActual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    /** 원 단위. */
    @Column(name = "actual_limit", nullable = false)
    private Long actualLimit;

    @Column(name = "actual_rate")
    private BigDecimal actualRate;

    @Column(name = "bank_name", length = 50)
    private String bankName;

    /** APPROVED | REJECTED. 반려면 actualLimit 이 null 이다. */
    @Column(nullable = false, length = 20)
    private String status = "APPROVED";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected LoanActual() {
    }

    public static LoanActual approved(Long userId, Long policyId, Long actualLimit,
                                      BigDecimal actualRate, String bankName) {
        LoanActual l = base(userId, policyId, bankName);
        l.actualLimit = actualLimit;
        l.actualRate = actualRate;
        l.status = "APPROVED";
        return l;
    }

    /** 반려는 금액·금리가 없다. 0 을 넣으면 "0원 승인" 과 섞이고 평균도 오염된다. */
    public static LoanActual rejected(Long userId, Long policyId, String bankName) {
        LoanActual l = base(userId, policyId, bankName);
        l.status = "REJECTED";
        return l;
    }

    private static LoanActual base(Long userId, Long policyId, String bankName) {
        LoanActual l = new LoanActual();
        l.userId = userId;
        l.policyId = policyId;
        l.bankName = bankName;
        return l;
    }

    public boolean isOwnedBy(Long uid) {
        return userId.equals(uid);
    }

    public Long getId() { return id; }
    public Long getActualLimit() { return actualLimit; }
    public String getBankName() { return bankName; }
    public BigDecimal getActualRate() { return actualRate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public boolean isRejected() { return "REJECTED".equals(status); }
}
