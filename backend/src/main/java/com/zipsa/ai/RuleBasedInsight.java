package com.zipsa.ai;

import com.zipsa.ai.dto.AiInsightResponse;
import com.zipsa.ai.dto.AiInsightResponse.Application;
import com.zipsa.news.News;
import com.zipsa.policy.Policy;
import com.zipsa.policy.RegionCodes;
import com.zipsa.user.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * LLM 없이 만드는 요약·분석. 두 가지 역할을 한다.
 *
 *   1) OPENAI_API_KEY 가 없을 때의 기본 동작
 *   2) LLM 호출이 실패했을 때의 폴백
 *
 * AI 는 보조 기능이다. 키가 없거나 OpenAI 가 죽어도 화면은 그대로 떠야 한다.
 * 여기서 나온 결과는 aiGenerated=false 로 표시되어 화면에 「샘플」 배지가 붙는다.
 *
 * 조건 판정(나이·지역·결혼·기간)은 여기서 계산하고, LLM 을 쓸 때도 그 결과를
 * 프롬프트에 넣어준다. 셈을 LLM 에 맡기면 틀린다.
 */
@Service
public class RuleBasedInsight {

    /** 문장 끝에서 자른다. 글자 수로 자르면 말이 중간에 끊긴다. */
    private static final Pattern SENTENCE = Pattern.compile("(?<=[.!?…])\\s+|(?<=다\\.)\\s*");
    private static final Pattern BULLET = Pattern.compile("^\\s*[○◯●□■\\-–·•※*①-⑩]\\s*|^\\s*\\([^)]{1,20}\\)\\s*");

    /* ────────────────────────── 정책 ────────────────────────── */

    public AiInsightResponse forPolicy(Policy policy, User user) {
        return new AiInsightResponse(
                summarizePolicy(policy),
                applyPolicy(policy, user),
                false);
    }

    private List<String> summarizePolicy(Policy p) {
        List<String> out = new ArrayList<>();

        // 1줄 — 무엇을 주는 정책인가. 본문 첫 문단이 개요 역할을 한다.
        String lead = firstMeaningful(p.getContent());
        out.add(lead != null ? lead : p.getTitle() + " 지원 정책입니다.");

        // 2줄 — 누가 받을 수 있나
        StringBuilder who = new StringBuilder("지원 대상은 ");
        if (p.getTargetMinAge() != null && p.getTargetMaxAge() != null) {
            who.append(String.format("만 %d~%d세", p.getTargetMinAge(), p.getTargetMaxAge()));
        } else {
            who.append("청년");
        }
        if (p.getMaritalCondition() != null && !p.getMaritalCondition().contains("제한없음")) {
            who.append(" ").append(p.getMaritalCondition().replace("|", "·"));
        }
        if (p.getRegion() != null && !p.getRegion().isBlank()) {
            who.append(", ").append(p.getRegion()).append(" 거주자");
        }
        who.append("입니다.");
        out.add(who.toString());

        // 3줄 — 언제까지 어떻게
        if (p.getApplyEndDate() != null) {
            long left = ChronoUnit.DAYS.between(LocalDate.now(), p.getApplyEndDate());
            out.add(left >= 0
                    ? String.format("신청은 %s까지이며 %d일 남았습니다.", p.getApplyEndDate(), left)
                    : String.format("신청 기간은 %s에 종료되었습니다.", p.getApplyEndDate()));
        } else {
            out.add("상시 신청할 수 있는 정책입니다.");
        }
        return out;
    }

    private Application applyPolicy(Policy p, User user) {
        List<String> reasons = new ArrayList<>();
        List<String> steps = new ArrayList<>();
        boolean blocked = false;

        // 조건이 "나에게 맞아서" 통과한 항목 수.
        // 조건 자체가 없어서 통과한 것과 구분해야 한다. 안 그러면 전부 같은 결론이 나온다.
        int matched = 0;

        int age = representativeAge(user.getAgeRange());
        boolean hasAgeRule = p.getTargetMinAge() != null || p.getTargetMaxAge() != null;
        if (hasAgeRule) {
            int min = p.getTargetMinAge() == null ? 0 : p.getTargetMinAge();
            int max = p.getTargetMaxAge() == null ? 200 : p.getTargetMaxAge();
            if (age >= min && age <= max) {
                reasons.add(String.format("%s은 이 정책의 나이 조건(만 %d~%d세)에 들어갑니다.",
                        label(user.getAgeRange()), min, max));
                matched++;
            } else {
                reasons.add(String.format("나이 조건이 만 %d~%d세라 %s은 대상이 아닙니다.",
                        min, max, label(user.getAgeRange())));
                blocked = true;
            }
        }

        String marital = p.getMaritalCondition();
        boolean hasMaritalRule = marital != null && !marital.contains("제한없음");
        if (hasMaritalRule) {
            String mine = user.getMaritalStatus() == MaritalStatus.MARRIED ? "기혼" : "미혼";
            if (marital.contains(mine)) {
                reasons.add(mine + " 대상 조건에 해당합니다.");
                matched++;
            } else {
                reasons.add(String.format("%s 대상 정책이라 %s인 경우 신청할 수 없습니다.",
                        marital.replace("|", "·"), mine));
                blocked = true;
            }
        }

        // 지역 — 가장 강력한 구분자. 정책 95%가 지자체 한정이다.
        String region = RegionCodes.displayOf(p.getSidoCodes());
        String myRegion = user.getRegion();
        boolean regionMismatch = false;
        if (region == null || "전국".equals(region)) {
            reasons.add("거주 지역 제한이 없는 전국 단위 정책입니다.");
            matched++;
        } else if (myRegion == null || myRegion.isBlank()) {
            reasons.add(String.format("%s 거주자 대상 정책입니다. 마이페이지에서 거주 지역을 등록하면"
                    + " 지역이 맞는 정책만 걸러 볼 수 있습니다.", region));
        } else if (RegionCodes.matches(p.getSidoCodes(), myRegion)) {
            reasons.add(String.format("거주 지역(%s)이 이 정책의 대상 지역과 일치합니다.", myRegion));
            matched++;
        } else {
            reasons.add(String.format("%s 거주자만 신청할 수 있는데 회원님은 %s 거주로 등록돼 있습니다.",
                    region, myRegion));
            regionMismatch = true;
            blocked = true;
        }

        if (p.getTargetJob() != null && !p.getTargetJob().contains("제한없음")) {
            reasons.add("취업 상태 조건이 있습니다: " + p.getTargetJob());
        }

        boolean open = p.isOpenOn(LocalDate.now());
        Long left = p.getApplyEndDate() == null ? null
                : ChronoUnit.DAYS.between(LocalDate.now(), p.getApplyEndDate());
        if (!open) {
            reasons.add(p.getApplyEndDate() != null && left != null && left < 0
                    ? String.format("신청이 %s에 마감됐습니다.", p.getApplyEndDate())
                    : "아직 신청 기간이 시작되지 않았습니다.");
        }

        /* ── 결론 ──
           "신청해 볼 만하다" 한 줄로 끝내면 200건이 넘는 정책이 전부 같은 말이 된다.
           조건이 없어서 통과한 것과 내 조건에 맞아서 통과한 것을 다르게 말한다. */
        String verdict;
        String tone;

        if (regionMismatch) {
            verdict = String.format("%s 지역 정책이라 신청할 수 없습니다.", region);
            tone = "caution";
            steps.add(String.format("%s 지역의 비슷한 정책을 「청년 정책」 목록에서 찾아보세요.", myRegion));
        } else if (blocked) {
            verdict = "지금 조건으로는 신청이 어려운 정책입니다.";
            tone = "caution";
            steps.add("조건이 맞는 다른 정책을 「청년 정책」 목록 위쪽의 맞춤 추천에서 확인해 보세요.");
        } else if (!open) {
            verdict = "조건은 맞지만 지금은 신청 기간이 아닙니다.";
            tone = "check";
            steps.add("마이페이지 「관심 정책」에 담아두면 다음 공고를 놓치지 않습니다.");
        } else if (region != null && (myRegion == null || myRegion.isBlank())) {
            verdict = String.format("%s에 거주 중이라면 신청할 수 있습니다.", region);
            tone = "check";
            steps.add("마이페이지에서 거주 지역을 등록하면 다음부터 자동으로 걸러 드립니다.");
        } else if (left != null && left <= 14) {
            verdict = String.format("조건이 맞습니다. 마감까지 %d일 남았습니다.", left);
            tone = "good";
            steps.add("서류부터 확인하세요. 마감이 가깝습니다.");
        } else if (matched >= 2) {
            verdict = "내 조건에 맞는 정책입니다.";
            tone = "good";
        } else if (!hasAgeRule && !hasMaritalRule) {
            // 조건이 없어서 통과한 경우. "맞는다" 고 말하면 과장이다.
            verdict = "별도 자격 제한이 없어 누구나 신청할 수 있습니다.";
            tone = "good";
        } else {
            verdict = "신청 자격에 해당합니다.";
            tone = "good";
        }

        if (!blocked && open) {
            steps.add("아래 「" + (p.getSourceName() == null ? "원문" : p.getSourceName())
                    + "에서 신청하기」로 이동해 신청 자격을 한 번 더 확인하세요.");
            if (p.getApplyMethod() != null && p.getApplyMethod().contains("방문")) {
                steps.add("방문 신청이 포함된 정책입니다. 주민센터 운영 시간을 미리 확인하세요.");
            }
            if (user.getJob() == Job.SELF_EMPLOYED || user.getJob() == Job.JOB_SEEKER) {
                steps.add("소득 증빙이 까다로운 조건입니다. 소득금액증명원을 미리 발급해 두세요.");
            }
        }
        return new Application(verdict, reasons, steps, tone);
    }

    /* ────────────────────────── 뉴스 ────────────────────────── */

    public AiInsightResponse forNews(News news, User user) {
        return new AiInsightResponse(summarizeNews(news), applyNews(news, user), false);
    }

    /**
     * 기사는 역피라미드 구조라 앞부분에 핵심이 몰려 있다.
     * 앞 문장부터 3개를 고르되 너무 짧은 조각(사진 설명 등)은 건너뛴다.
     */
    private List<String> summarizeNews(News news) {
        String body = news.getContent() != null && !news.getContent().isBlank()
                ? news.getContent() : news.getSummary();
        if (body == null || body.isBlank()) {
            return List.of(news.getTitle());
        }
        List<String> out = new ArrayList<>();
        for (String raw : SENTENCE.split(body)) {
            String s = raw.replaceAll("\\s+", " ").trim();
            if (s.length() < 25 || s.length() > 160) continue;
            out.add(s);
            if (out.size() == 3) break;
        }
        return out.isEmpty() ? List.of(news.getTitle()) : out;
    }

    /** 기사 주제를 키워드로 판별해 회원 조건과 연결한다. */
    private Application applyNews(News news, User user) {
        String blob = (news.getTitle() + " " + (news.getContent() == null ? "" : news.getContent()));
        List<String> reasons = new ArrayList<>();
        List<String> steps = new ArrayList<>();
        String tone = "check";
        String verdict;

        boolean jeonse = blob.matches("(?s).*(전세|보증금|임차|전세사기).*");
        boolean loan = blob.matches("(?s).*(대출|금리|이자|한도|LTV|DSR).*");
        boolean supply = blob.matches("(?s).*(공급|분양|청약|입주|착공).*");
        boolean tax = blob.matches("(?s).*(종부세|세제|양도세|취득세).*");

        String me = String.format("%s · %s · 연소득 %s",
                label(user.getAgeRange()), label(user.getJob()), label(user.getSalaryRange()));

        if (loan) {
            reasons.add(me + " 조건이면 대출 한도와 금리가 직접 영향을 받는 기사입니다.");
            steps.add("「대출예측」에서 지금 조건의 은행별 예상 한도를 다시 확인해 보세요.");
            verdict = "대출 조건에 영향이 있을 수 있는 소식입니다.";
        } else if (jeonse) {
            reasons.add("전월세 계약을 앞두고 있다면 확인해 둘 내용입니다.");
            steps.add("계약 전 등기부등본의 근저당과 전세보증보험 가입 여부를 확인하세요.");
            verdict = "전월세 계약 전에 알아둘 내용입니다.";
        } else if (supply) {
            reasons.add("공급·청약 관련 소식이라 공공임대 모집 일정과 함께 보면 좋습니다.");
            steps.add("「공공임대 정보확인」의 모집 캘린더에서 해당 지역 공고를 확인해 보세요.");
            verdict = "청약·공급 일정과 함께 볼 소식입니다.";
        } else if (tax) {
            reasons.add("세제 관련 소식입니다. 무주택 청년에게는 당장 영향이 크지 않습니다.");
            steps.add("당장 조치할 일은 없습니다. 시장 흐름 참고용으로만 보세요.");
            verdict = "지금 당장 영향은 크지 않은 소식입니다.";
            tone = "good";
        } else {
            reasons.add("주거 시장 전반에 대한 소식입니다.");
            steps.add("특별히 조치할 일은 없습니다.");
            verdict = "참고로 알아둘 만한 소식입니다.";
            tone = "good";
        }

        if (user.getMaritalStatus() == MaritalStatus.MARRIED && blob.contains("신혼")) {
            reasons.add("신혼부부 대상 내용이 포함되어 있어 해당될 수 있습니다.");
        }
        return new Application(verdict, reasons, steps, tone);
    }

    /* ────────────────────────── 공통 ────────────────────────── */

    private String firstMeaningful(String content) {
        if (content == null) return null;
        for (String line : content.split("\n")) {
            String s = BULLET.matcher(line.trim()).replaceAll("").trim();
            if (s.length() >= 20) {
                return s.length() > 140 ? s.substring(0, 140) + "…" : s;
            }
        }
        return null;
    }

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
