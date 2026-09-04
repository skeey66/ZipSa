package com.zipsa.ai;

import com.zipsa.ai.dto.AiInsightResponse;
import com.zipsa.common.ApiResponse;
import com.zipsa.common.BusinessException;
import com.zipsa.common.ErrorCode;
import com.zipsa.news.NewsRepository;
import com.zipsa.policy.PolicyRepository;
import com.zipsa.user.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 정책·뉴스 상세의 AI 자리.
 *
 * 「나에게 어떻게 적용되나」가 회원 조건을 쓰므로 로그인이 필요하다.
 * 비로그인에게는 화면에서 이 영역을 감춘다(401 을 띄우지 않는다).
 */
@RestController
@RequestMapping("/api/ai")
public class AiInsightController {

    private final AiInsightService service;
    private final PolicyRepository policyRepository;
    private final NewsRepository newsRepository;
    private final UserRepository userRepository;

    public AiInsightController(AiInsightService service, PolicyRepository policyRepository,
                               NewsRepository newsRepository, UserRepository userRepository) {
        this.service = service;
        this.policyRepository = policyRepository;
        this.newsRepository = newsRepository;
        this.userRepository = userRepository;
    }

    /** POLICY-005 — 정책 요약 + 내 적용 */
    @GetMapping("/policies/{policyId}")
    public ApiResponse<AiInsightResponse> policy(@AuthenticationPrincipal Long userId,
                                                 @PathVariable Long policyId) {
        var policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POLICY_NOT_FOUND));
        return ApiResponse.ok(service.forPolicy(policy, user(userId)));
    }

    /** 뉴스 요약 + 내 적용 */
    @GetMapping("/news/{newsId}")
    public ApiResponse<AiInsightResponse> news(@AuthenticationPrincipal Long userId,
                                               @PathVariable Long newsId) {
        var news = newsRepository.findById(newsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NEWS_NOT_FOUND));
        return ApiResponse.ok(service.forNews(news, user(userId)));
    }

    private com.zipsa.user.User user(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
