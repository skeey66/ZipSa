package com.zipsa.loan;

import com.zipsa.ai.AiAvailability;
import com.zipsa.loan.dto.LoanPredictionResponse.AnalysisReport;
import com.zipsa.loan.dto.LoanPredictionResponse.BankPrediction;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 화면 14 「분석레포트」를 LLM 으로 쓴다. 키가 없으면 규칙 기반 문장을 그대로 쓴다.
 *
 * 예상 한도·금리·표본 수는 전부 코드가 계산해서 넘긴다.
 * LLM 은 그 숫자를 문장으로 엮는 역할만 한다 — 금액을 LLM 이 만들면 틀린다.
 */
@Component
public class LoanReportWriter {

    private static final Logger log = LoggerFactory.getLogger(LoanReportWriter.class);

    private static final String SYSTEM = """
            너는 금융 데이터 분석 리포트를 쓰는 애널리스트다. 존댓말 서면체로, 사실만 건조하게 쓴다.

            반드시 지킬 것
            - 제공된 숫자만 쓴다. 금액·금리·건수·비율을 새로 만들거나 반올림해 바꾸지 않는다.
            - 각 절은 2~3문장. 수치를 먼저 제시하고 해석을 뒤에 붙인다.
            - 한도가 큰 곳과 금리가 낮은 곳이 다르면 그 상충을 명시한다.
            - 특정 은행을 권유하지 않는다. 판단 재료만 제시한다.
            - "반드시", "무조건", "확실히" 같은 단정적 표현을 쓰지 않는다.
            - 절 제목은 바꾸지 않는다. 본문만 다시 쓴다.
            """;

    private final AiAvailability availability;
    private final ObjectProvider<ChatClient.Builder> builder;

    public LoanReportWriter(AiAvailability availability, ObjectProvider<ChatClient.Builder> builder) {
        this.availability = availability;
        this.builder = builder;
    }

    /** LLM 은 각 절의 본문만 다시 쓴다. 제목·지표·한계는 코드가 만든 것을 그대로 쓴다. */
    public record Draft(List<DraftSection> sections) {
        public record DraftSection(String title, List<String> body) {
        }
    }

    /**
     * @param fallback 규칙 기반으로 만든 레포트. 키가 없거나 실패하면 이게 그대로 나간다.
     */
    public AnalysisReport write(String profile, List<BankPrediction> banks, AnalysisReport fallback) {
        if (!availability.isConfigured()) {
            return fallback;
        }
        ChatClient.Builder b = builder.getIfAvailable();
        if (b == null) {
            return fallback;
        }

        try {
            String table = banks.stream()
                    .map(x -> String.format("- %s: 예상 한도 %,d원, 금리 연 %.2f%%, 표본 %d건",
                            x.bankName(), x.expectedLimit(), x.expectedRate(), x.sampleSize()))
                    .reduce((x, y) -> x + "\n" + y).orElse("");

            String outline = fallback.sections().stream()
                    .map(sec -> sec.title() + "\n" + String.join("\n", sec.body()))
                    .reduce((x, y) -> x + "\n\n" + y).orElse("");

            Draft draft = b.defaultSystem(SYSTEM).build().prompt()
                    .user(u -> u.text("""
                            아래 회원 조건의 대출 분석 리포트를 다시 써 줘.
                            절 제목은 그대로 두고 본문 문장만 더 분석적으로 고쳐 줘.

                            [회원] {profile}

                            [은행별 예상]
                            {table}

                            [현재 초안]
                            {outline}
                            """)
                            .param("profile", profile)
                            .param("table", table)
                            .param("outline", outline))
                    .call()
                    .entity(Draft.class);

            if (draft == null || draft.sections() == null || draft.sections().isEmpty()) {
                return fallback;
            }
            List<AnalysisReport.Section> rewritten = draft.sections().stream()
                    .filter(x -> x.title() != null && x.body() != null && !x.body().isEmpty())
                    .map(x -> new AnalysisReport.Section(x.title(), x.body()))
                    .toList();
            if (rewritten.isEmpty()) {
                return fallback;
            }
            // 숫자가 실린 지표·한계·면책은 LLM 것을 쓰지 않는다. 문장만 교체한다.
            return new AnalysisReport(
                    fallback.headline(), fallback.scope(), fallback.metrics(), rewritten,
                    fallback.recommendedBank(), fallback.limitations(), fallback.disclaimer(), true);
        } catch (Exception e) {
            log.warn("분석레포트 AI 생성 실패 — 규칙 기반으로 대체합니다: {}", e.getMessage());
            return fallback;
        }
    }
}
