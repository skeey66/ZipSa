package com.zipsa.ai;

import com.zipsa.ai.dto.AiInsightResponse;
import com.zipsa.ai.dto.AiInsightResponse.Application;
import com.zipsa.news.News;
import com.zipsa.policy.Policy;
import com.zipsa.policy.RegionCodes;
import com.zipsa.user.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * LLM 으로 요약·적용 분석을 만든다. OPENAI_API_KEY 가 있을 때만 호출된다.
 *
 * ■ 설계 원칙
 *   1) 숫자는 LLM 에 맡기지 않는다.
 *      나이 충족 여부·마감까지 남은 일수·지역 일치는 코드가 계산해서 프롬프트에 넣는다.
 *      LLM 이 날짜를 빼면 틀린다.
 *   2) 결론(tone)도 코드가 정한다.
 *      "신청 가능/불가" 는 규칙으로 판정할 수 있는 것이고, 틀리면 사용자가 손해를 본다.
 *      LLM 은 그 판정을 사람이 읽을 문장으로 풀어쓰는 역할만 한다.
 *   3) 실패하면 조용히 폴백한다. AI 가 죽어도 본문은 읽을 수 있어야 한다.
 */
@Component
public class LlmInsight {

    private static final String SYSTEM = """
            너는 한국의 청년 주거 서비스 'ZIP 보금자리'의 정책 안내 도우미다.
            사용자에게 존댓말로, 군더더기 없이 사실만 전달한다.

            반드시 지킬 것
            - 제공된 사실만 사용한다. 없는 금액·날짜·조건을 지어내지 않는다.
            - 이미 계산된 판정(신청 가능 여부, 남은 일수)을 그대로 따른다. 다시 계산하지 않는다.
            - summary 는 3문장. 각 문장은 한 줄로 끝낸다.
            - verdict(한 줄 결론)는 이미 확정돼 있다. 바꾸거나 뒤집지 않는다.
            - reasons 는 판정의 근거를 2~4개.
            - nextSteps 는 사용자가 지금 할 수 있는 구체적인 행동을 1~3개.
            - 과장하거나 권유하지 않는다. "꼭 신청하세요" 같은 표현은 쓰지 않는다.
            """;

    private final ChatClient chatClient;

    public LlmInsight(ChatClient.Builder builder) {
        this.chatClient = builder.defaultSystem(SYSTEM).build();
    }

    /** LLM 응답 구조. Spring AI 가 이 레코드로 바인딩한다. */
    public record Draft(
            List<String> summary,
            String verdict,
            List<String> reasons,
            List<String> nextSteps
    ) {
    }

    public AiInsightResponse forPolicy(Policy p, User user, Verdict verdict) {
        Draft draft = chatClient.prompt()
                .user(u -> u.text("""
                        아래 청년 정책을 요약하고, 이 회원에게 어떻게 적용되는지 설명해 줘.

                        [정책]
                        제목: {title}
                        분류: {category}
                        대상 지역: {region}
                        주관: {issuer}
                        지원 나이: {age}
                        소득 조건: {salary}
                        신청 기간: {period}
                        신청 방법: {method}
                        내용:
                        {content}

                        [회원]
                        {profile}

                        [이미 확정된 판정 — 이대로 따를 것]
                        결론: {verdict}
                        판정 근거: {facts}
                        """)
                        .param("title", p.getTitle())
                        .param("category", p.getCategory().label())
                        .param("region", nz(RegionCodes.displayOf(p.getSidoCodes()), "정보 없음"))
                        .param("issuer", nz(p.getIssuer(), "정보 없음"))
                        .param("age", nz(p.getTargetAgeRange(), "제한 없음"))
                        .param("salary", nz(p.getTargetSalaryRange(), "제한 없음"))
                        .param("period", period(p))
                        .param("method", nz(p.getApplyMethod(), "원문 참고"))
                        // 정책 본문이 아주 긴 경우가 있어 토큰을 아끼려고 자른다.
                        .param("content", trim(p.getContent(), 1800))
                        .param("profile", profile(user))
                        .param("verdict", verdict.headline())
                        .param("facts", String.join("\n- ", verdict.facts())))
                .call()
                .entity(Draft.class);

        return toResponse(draft, verdict);
    }

    public AiInsightResponse forNews(News news, User user, Verdict verdict) {
        Draft draft = chatClient.prompt()
                .user(u -> u.text("""
                        아래 기사를 3문장으로 요약하고, 이 회원의 주거 상황에 어떤 의미인지 설명해 줘.

                        [기사]
                        제목: {title}
                        언론사: {press}
                        본문:
                        {content}

                        [회원]
                        {profile}

                        [참고 판정]
                        {verdict}
                        """)
                        .param("title", news.getTitle())
                        .param("press", nz(news.getPressName(), "-"))
                        .param("content", trim(
                                news.getContent() != null && !news.getContent().isBlank()
                                        ? news.getContent() : news.getSummary(), 2500))
                        .param("profile", profile(user))
                        .param("verdict", verdict.headline()))
                .call()
                .entity(Draft.class);

        return toResponse(draft, verdict);
    }

    /**
     * LLM 이 쓴 문장과 코드가 내린 판정을 합친다.
     *
     * ⚠️ verdict(한 줄 결론)는 LLM 것을 쓰지 않고 코드가 정한 것을 그대로 쓴다.
     *    목 서버로 검증하다 발견한 문제인데, LLM 이 "신청해 볼 만한 정책입니다" 라고 쓰고
     *    tone 은 코드가 정한 caution 이 남아서, 화면에 ⚠️ 아이콘과 함께
     *    "신청해 볼 만한 정책입니다" 가 같이 뜬 적이 있다.
     *    결론과 아이콘이 어긋나면 사용자가 신청 가능 여부를 잘못 판단한다.
     *    LLM 은 이유와 다음 행동을 사람 말로 풀어쓰는 역할만 한다.
     *
     * 빈 응답이 오면 폴백하도록 예외를 던진다 — 빈 화면을 보여주느니 규칙 기반이 낫다.
     */
    private AiInsightResponse toResponse(Draft d, Verdict verdict) {
        if (d == null || d.summary() == null || d.summary().isEmpty()) {
            throw new IllegalStateException("LLM 응답이 비어 있습니다.");
        }
        return new AiInsightResponse(
                d.summary(),
                new Application(
                        verdict.headline(),
                        d.reasons() == null || d.reasons().isEmpty() ? verdict.facts() : d.reasons(),
                        d.nextSteps() == null ? List.of() : d.nextSteps(),
                        verdict.tone()),
                true);
    }

    /* ── 프롬프트 재료 ────────────────────────── */

    /** 코드가 계산한 판정. LLM 은 이걸 뒤집지 못한다. */
    public record Verdict(String headline, List<String> facts, String tone) {
    }

    private String profile(User user) {
        return String.format("%s · %s · 연소득 %s · %s · 거주 %s",
                label(user.getAgeRange()), label(user.getJob()), label(user.getSalaryRange()),
                user.getMaritalStatus() == MaritalStatus.MARRIED ? "기혼" : "미혼",
                nz(user.getRegion(), "미등록"));
    }

    private String period(Policy p) {
        if (p.getApplyEndDate() == null) return "상시";
        long left = ChronoUnit.DAYS.between(LocalDate.now(), p.getApplyEndDate());
        return String.format("%s ~ %s (%s)",
                p.getApplyStartDate() == null ? "상시" : p.getApplyStartDate(),
                p.getApplyEndDate(),
                left >= 0 ? "마감까지 " + left + "일" : "마감됨");
    }

    private String trim(String s, int max) {
        if (s == null || s.isBlank()) return "(내용 없음)";
        String flat = s.strip();
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }

    private String nz(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }

    private String label(AgeRange v) {
        if (v == null) return "연령 미상";
        return switch (v) {
            case AGE_10S -> "10대";
            case AGE_20S_EARLY -> "20대 초반";
            case AGE_20S_LATE -> "20대 후반";
            case AGE_30S_EARLY -> "30대 초반";
            case AGE_30S_LATE -> "30대 후반";
            case AGE_40S_OVER -> "40대 이상";
        };
    }

    private String label(Job v) {
        if (v == null) return "직업 미상";
        return switch (v) {
            case STUDENT -> "학생";
            case EMPLOYEE -> "직장인";
            case SELF_EMPLOYED -> "자영업";
            case JOB_SEEKER -> "취업준비생";
            case ETC -> "기타";
        };
    }

    private String label(SalaryRange v) {
        if (v == null) return "미상";
        return switch (v) {
            case UNDER_2000 -> "2천만원 미만";
            case RANGE_2000_3000 -> "2~3천만원";
            case RANGE_3000_4000 -> "3~4천만원";
            case RANGE_4000_5000 -> "4~5천만원";
            case RANGE_5000_7000 -> "5~7천만원";
            case OVER_7000 -> "7천만원 이상";
        };
    }
}
