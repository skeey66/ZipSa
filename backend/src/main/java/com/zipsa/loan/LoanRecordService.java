package com.zipsa.loan;

import com.zipsa.common.BusinessException;
import com.zipsa.common.ErrorCode;
import com.zipsa.loan.dto.LoanRecordRequest;
import com.zipsa.loan.dto.MyLoanResponse;
import com.zipsa.policy.PolicyRepository;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 대출 결과 등록·조회. 등록하면 커뮤니티에 은행 뱃지가 붙는다. */
@Service
@Transactional(readOnly = true)
public class LoanRecordService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final LoanActualRepository loanActualRepository;
    private final PolicyRepository policyRepository;

    public LoanRecordService(LoanActualRepository loanActualRepository,
                             PolicyRepository policyRepository) {
        this.loanActualRepository = loanActualRepository;
        this.policyRepository = policyRepository;
    }

    @Transactional
    public Long record(Long userId, LoanRecordRequest request) {
        BankCode bank;
        try {
            bank = BankCode.valueOf(request.bankName());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 은행입니다.");
        }

        // loan_actuals.policy_id 가 NOT NULL 이다. 사용자가 정책을 고르지 않았으면
        // 대출 성격의 정책 아무거나가 아니라 "가장 최근에 신청 가능한" 것을 붙인다.
        Long policyId = request.policyId() != null
                ? request.policyId()
                : policyRepository.findRepresentativeLoanPolicyId()
                        .orElseThrow(() -> new BusinessException(ErrorCode.POLICY_NOT_FOUND,
                                "연결할 대출 정책이 없습니다. 정책 데이터를 먼저 수집하세요."));

        LoanActual entity;
        if (request.rejected()) {
            entity = LoanActual.rejected(userId, policyId, bank.displayName());
        } else {
            if (request.actualLimit() == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "승인 금액을 입력해 주세요.");
            }
            entity = LoanActual.approved(userId, policyId, request.actualLimit(),
                    request.actualRate() == null ? null : BigDecimal.valueOf(request.actualRate()),
                    bank.displayName());
        }
        return loanActualRepository.save(entity).getId();
    }

    public MyLoanResponse myLoans(Long userId) {
        List<MyLoanResponse.Item> items = loanActualRepository
                .findByUserIdOrderByIdDesc(userId).stream()
                .map(l -> new MyLoanResponse.Item(
                        l.getId(),
                        l.getBankName(),
                        BankCode.fromDisplayName(l.getBankName()).map(Enum::name).orElse(null),
                        l.isRejected(),
                        l.getActualLimit(),
                        l.getActualRate() == null ? null : l.getActualRate().doubleValue(),
                        l.getCreatedAt() == null ? null : l.getCreatedAt().format(DATE)))
                .toList();
        return new MyLoanResponse(items);
    }

    @Transactional
    public void delete(Long userId, Long loanId) {
        LoanActual loan = loanActualRepository.findById(loanId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_LOAN_SAMPLE));
        if (!loan.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "본인이 등록한 기록만 삭제할 수 있습니다.");
        }
        loanActualRepository.delete(loan);
    }
}
