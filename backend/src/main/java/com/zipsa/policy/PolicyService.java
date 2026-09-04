package com.zipsa.policy;

import com.zipsa.common.BusinessException;
import com.zipsa.common.ErrorCode;
import com.zipsa.policy.dto.PolicyResponse;
import com.zipsa.policy.dto.RecommendResponse;
import com.zipsa.user.AgeRange;
import com.zipsa.user.MaritalStatus;
import com.zipsa.user.User;
import com.zipsa.user.UserRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final UserRepository userRepository;

    public PolicyService(PolicyRepository policyRepository, UserRepository userRepository) {
        this.policyRepository = policyRepository;
        this.userRepository = userRepository;
    }

    /** POLICY-001 — 목록/검색/필터 */
    public Page<PolicyResponse> search(String keyword, String region, PolicyCategory category,
                                       boolean openOnly, Pageable pageable) {
        LocalDate today = LocalDate.now();
        return policyRepository
                .search(blankToNull(keyword), blankToNull(region), category, openOnly, today, pageable)
                .map(p -> PolicyResponse.listItem(p, today));
    }

    /** POLICY-002 — 상세 */
    public PolicyResponse get(Long policyId) {
        return policyRepository.findById(policyId)
                .map(p -> PolicyResponse.detail(p, LocalDate.now()))
                .orElseThrow(() -> new BusinessException(ErrorCode.POLICY_NOT_FOUND));
    }

    /**
     * POLICY-004 — 맞춤 정책.
     *
     * 규칙 기반이다. LLM 을 쓰지 않는 이유:
     *   · 같은 프로필이면 결과가 같아야 한다(호출마다 달라지면 사용자가 신뢰하지 않는다)
     *   · 왜 추천됐는지 설명할 수 있어야 한다
     *   · 회원 수만큼 비용이 곱해지지 않는다
     */
    public List<RecommendResponse> recommend(Long userId, int size) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        LocalDate today = LocalDate.now();
        int age = representativeAge(user.getAgeRange());

        return policyRepository.findRecommendCandidates(age, today).stream()
                .map(p -> score(p, user, age, today))
                .filter(r -> r.relevanceRate() > 0)
                .sorted(Comparator.comparingInt(RecommendResponse::relevanceRate).reversed()
                        .thenComparing(r -> r.dDay() == null ? Long.MAX_VALUE : r.dDay()))
                .limit(size)
                .toList();
    }

    private RecommendResponse score(Policy p, User user, int age, LocalDate today) {
        List<String> reasons = new ArrayList<>();
        int rate = 40;   // 나이 조건을 통과해 후보로 올라온 것 자체가 기본 점수

        if (p.getTargetMinAge() != null || p.getTargetMaxAge() != null) {
            reasons.add("나이대 조건 충족");
            rate += 20;
        }

        // 결혼 조건은 '제한없음|기혼|미혼' 처럼 파이프로 붙어 온다.
        String marital = p.getMaritalCondition();
        if (marital != null && !marital.contains("제한없음")) {
            String wanted = user.getMaritalStatus() == MaritalStatus.MARRIED ? "기혼" : "미혼";
            if (marital.contains(wanted)) {
                reasons.add(wanted + " 대상 정책");
                rate += 20;
            } else {
                return new RecommendResponse(p.getId(), p.getTitle(), p.getCategory().label(),
                        p.getRegion(), null, 0, List.of());   // 결혼 조건이 어긋나면 제외
            }
        }

        // 지역 — 정책 대부분이 지자체 한정이라 가장 큰 구분자다.
        // 기관명 문자열이 아니라 API 가 준 법정동코드로 비교한다(RegionCodes 주석 참고).
        String myRegion = user.getRegion();
        if (RegionCodes.NATIONWIDE.equals(p.getSidoCodes())) {
            reasons.add("전국 대상 정책");
            rate += 5;
        } else if (myRegion != null && !myRegion.isBlank() && p.getSidoCodes() != null) {
            if (RegionCodes.matches(p.getSidoCodes(), myRegion)) {
                reasons.add(myRegion + " 지역 정책");
                rate += 25;
            } else {
                // 신청할 수 없는 걸 추천하면 추천이 아니다.
                return new RecommendResponse(p.getId(), p.getTitle(), p.getCategory().label(),
                        p.getRegion(), null, 0, List.of());
            }
        }

        if (p.getCategory() == PolicyCategory.LOAN || p.getCategory() == PolicyCategory.PUBLIC_HOUSING) {
            reasons.add("주거비 부담 완화");
            rate += 10;
        }

        Long dDay = p.getApplyEndDate() == null ? null
                : Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(today, p.getApplyEndDate()));
        if (dDay != null && dDay <= 30) {
            reasons.add("마감 임박");
            rate += 10;
        }

        return new RecommendResponse(p.getId(), p.getTitle(), p.getCategory().label(),
                p.getRegion(), dDay, Math.min(100, rate), reasons);
    }

    /** 나이대 enum 을 대표 나이 하나로 바꾼다. 정책의 나이 범위와 비교하기 위한 값이다. */
    private int representativeAge(AgeRange range) {
        if (range == null) return 27;
        return switch (range) {
            case AGE_10S -> 19;
            case AGE_20S_EARLY -> 22;
            case AGE_20S_LATE -> 27;
            case AGE_30S_EARLY -> 32;
            case AGE_30S_LATE -> 37;
            case AGE_40S_OVER -> 42;
        };
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
