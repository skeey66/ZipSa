package com.zipsa.mock;

import com.zipsa.ai.dto.AiInsightResponse;
import com.zipsa.common.ApiResponse;
import com.zipsa.loan.dto.LoanPredictionResponse.AnalysisReport;
import com.zipsa.mock.dto.MockAiStatusResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 연동 대비 자리 — 항상 같은 정적 응답을 돌려준다.
 *
 * <p>실제 엔드포인트(/api/ai/**, /api/loans/prediction)와 <b>응답 스키마가 동일</b>하다.
 * 프론트는 base 경로만 {@code /api/ai} ↔ {@code /api/mock/ai} 로 바꿔 끼우면 되고,
 * 백엔드·AI 작업이 끝나기 전에 화면을 먼저 만들 수 있다.
 *
 * <p>규칙:
 * <ul>
 *   <li>DB 를 읽지 않는다 — 로그인도, 데이터 적재도 필요 없다.</li>
 *   <li>404 를 내지 않는다 — 없는 ID 를 넣어도 같은 목업을 돌려준다.</li>
 *   <li>모든 문장에 {@code [목업]} 을 붙이고 {@code aiGenerated=false} 로 내린다.
 *       목업을 진짜처럼 보이게 두면 시연에서 오해가 생긴다.</li>
 * </ul>
 *
 * <p>명세: docs/api/API.yml 의 <b>Mock</b> 태그.
 */
@RestController
@RequestMapping("/api/mock/ai")
public class MockAiController {

    /** [MOCK] AI 사용 가능 여부 */
    @GetMapping("/status")
    public ApiResponse<MockAiStatusResponse> status() {
        return ApiResponse.ok(MockAiStatusResponse.notConfigured());
    }

    /** [MOCK] 정책 AI 요약 + 내 적용. policyId 는 형태를 맞추기 위한 것으로, 값은 쓰지 않는다. */
    @GetMapping("/policies/{policyId}")
    public ApiResponse<AiInsightResponse> policyInsight(@PathVariable Long policyId) {
        return ApiResponse.ok(new AiInsightResponse(
                List.of(
                        "[목업] 만 19~39세 무주택 청년의 전월세 보증금 대출 이자를 지원하는 제도입니다.",
                        "[목업] 지원 한도는 연 2% 이내, 최대 2년입니다.",
                        "[목업] 신청은 정부24 온라인 또는 관할 구청 방문으로 가능합니다."),
                new AiInsightResponse.Application(
                        "[목업] 조건에 해당할 가능성이 높습니다.",
                        List.of("[목업] 나이대 조건 충족", "[목업] 소득 구간 조건 충족"),
                        List.of("[목업] 주민등록등본 발급", "[목업] 임대차계약서 준비"),
                        "good"),
                false));
    }

    /** [MOCK] 뉴스 AI 요약 + 내 적용 */
    @GetMapping("/news/{newsId}")
    public ApiResponse<AiInsightResponse> newsInsight(@PathVariable Long newsId) {
        return ApiResponse.ok(new AiInsightResponse(
                List.of(
                        "[목업] 정부가 청년 전세대출 이자지원 대상을 확대한다고 발표했습니다.",
                        "[목업] 소득 기준이 연 4,000만원에서 5,000만원으로 올라갑니다.",
                        "[목업] 시행 시점은 내년 1분기로 예고됐습니다."),
                new AiInsightResponse.Application(
                        "[목업] 확대안이 시행되면 새로 대상에 포함될 수 있습니다.",
                        List.of("[목업] 현재 소득이 기존 기준을 조금 넘습니다"),
                        List.of("[목업] 시행 공고를 알림으로 받아두기"),
                        "check"),
                false));
    }

    /** [MOCK] 화면 14 하단 「분석레포트」 — 로그인·표본 없이 개발하기 위한 자리 */
    @GetMapping("/loans/report")
    public ApiResponse<AnalysisReport> loanReport() {
        return ApiResponse.ok(new AnalysisReport(
                "[목업] 예상 한도 1억 500만원 ~ 1억 3,000만원",
                "[목업] 분석 대상 20대 후반 · 미혼 · 직장인 · 연소득 3~4천만원  |  표본 320건  |  기준일 2026-01-01",
                List.of(
                        new AnalysisReport.Metric("예상 한도", "1억 500만원 ~ 1억 3,000만원", "은행 4곳 기준"),
                        new AnalysisReport.Metric("최저 금리", "연 3.25%", "NH농협은행"),
                        new AnalysisReport.Metric("승인율", "83.8%", "표본 320건 중 268건"),
                        new AnalysisReport.Metric("은행 간 편차", "2,500만원", "최저 대비 23.8%")),
                List.of(
                        new AnalysisReport.Section("1. 한도 분석", List.of(
                                "[목업] 예상 한도는 KB국민은행이 1억 3,000만원으로 가장 높습니다.")),
                        new AnalysisReport.Section("2. 금리·비용 분석", List.of(
                                "[목업] 금리 스프레드는 0.65%p 입니다.")),
                        new AnalysisReport.Section("3. 승인 가능성", List.of(
                                "[목업] 표본의 8%는 반려됐습니다."))),
                "KB국민은행",
                List.of("[목업] 표본은 회원 자발적 입력이라 승인율이 높게 나타날 수 있습니다."),
                "목업 데이터입니다. 실제 심사 결과와 무관합니다.",
                false));
    }
}
