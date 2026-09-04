package com.zipsa.policy;

import com.zipsa.common.ApiResponse;
import com.zipsa.policy.dto.PolicyResponse;
import com.zipsa.policy.dto.RecommendResponse;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 화면 01 메인 · 06 정책리스트 · 07 정책상세. */
@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    private final PolicyService service;

    public PolicyController(PolicyService service) {
        this.service = service;
    }

    /** POLICY-001 — 목록/검색/필터 */
    @GetMapping
    public ApiResponse<Page<PolicyResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) PolicyCategory category,
            @RequestParam(defaultValue = "false") boolean openOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.search(keyword, region, category, openOnly,
                PageRequest.of(page, size)));
    }

    /** POLICY-004 — 맞춤 정책. 로그인 필요. */
    @GetMapping("/recommend")
    public ApiResponse<List<RecommendResponse>> recommend(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(service.recommend(userId, size));
    }

    /** POLICY-002 — 상세 */
    @GetMapping("/{policyId}")
    public ApiResponse<PolicyResponse> get(@PathVariable Long policyId) {
        return ApiResponse.ok(service.get(policyId));
    }
}
