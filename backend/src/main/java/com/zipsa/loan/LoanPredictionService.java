package com.zipsa.loan;

import com.zipsa.ai.AiAvailability;
import com.zipsa.common.BusinessException;
import com.zipsa.common.ErrorCode;
import com.zipsa.loan.dto.LoanPredictionResponse;
import com.zipsa.loan.dto.LoanPredictionResponse.AnalysisReport;
import com.zipsa.loan.dto.LoanPredictionResponse.AnalysisReport.Metric;
import com.zipsa.loan.dto.LoanPredictionResponse.AnalysisReport.Section;
import com.zipsa.loan.dto.LoanPredictionResponse.BankPrediction;
import com.zipsa.loan.dto.LoanPredictionResponse.MyProfile;
import com.zipsa.loan.dto.LoanSampleResponse;
import com.zipsa.loan.dto.LoanSampleResponse.Mine;
import com.zipsa.loan.dto.LoanSampleResponse.Sample;
import com.zipsa.user.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 화면 14 「나의 대출 예측하기」.
 *
 * 그래프는 회원들이 실제로 승인받은 이력(loan_actuals)을 구간별로 센 것이다.
 * 예상 한도는 그 표본에서 "나와 조건이 비슷한 사람들" 의 분포를 보고 정한다.
 *
 * ⚠️ 분석레포트는 지금 규칙 기반 목업이다. AI 키가 붙으면 교체한다.
 *    응답의 aiGenerated=false 로 그 사실을 프론트까지 전달한다 —
 *    "AI 분석" 이라고 써두고 실제로는 아닌 상태를 화면이 모르면 안 된다.
 */
@Service
@Transactional(readOnly = true)
public class LoanPredictionService {

    /**
     * 막대 5칸, 0.5억 단위.
     * 처음에 0.8억으로 끊었더니 예상 한도가 전부 같은 칸에 몰려 카드 5개의 강조 위치가
     * 똑같아졌다. 그러면 "은행마다 다르다" 를 보여주는 화면의 목적이 사라진다.
     */
    private static final long BUCKET_SIZE = 50_000_000L;

    /** 0번 칸은 「반려」다. 승인 금액만 보여주면 떨어질 가능성을 알 수 없다. */
    private static final int BUCKET_COUNT = 6;
    private static final int REJECTED_BUCKET = 0;

    private static final List<String> BUCKET_LABELS =
            List.of("반려", "0.5억 미만", "0.5~1억", "1~1.5억", "1.5~2억", "2억 이상");

    /** 카드 색. 와이어프레임의 노랑·파랑·초록·보라 순서를 따른다. */
    private static final Map<String, String> THEME = Map.of(
            "KB국민은행", "amber",
            "우리은행", "blue",
            "NH농협은행", "green",
            "하나은행", "violet");

    private final LoanActualRepository loanActualRepository;
    private final UserRepository userRepository;
    private final AiAvailability aiAvailability;
    private final LoanReportWriter reportWriter;

    public LoanPredictionService(LoanActualRepository loanActualRepository,
                                 UserRepository userRepository,
                                 AiAvailability aiAvailability,
                                 LoanReportWriter reportWriter) {
        this.loanActualRepository = loanActualRepository;
        this.userRepository = userRepository;
        this.aiAvailability = aiAvailability;
        this.reportWriter = reportWriter;
    }

    public LoanPredictionResponse predict(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 1) 전체 표본으로 은행별 분포를 만든다(막대 높이).
        Map<String, long[]> distribution = new LinkedHashMap<>();
        Map<String, Long> sampleSize = new HashMap<>();
        Map<String, Long> avgLimit = new HashMap<>();
        Map<String, Double> avgRate = new HashMap<>();

        for (var row : loanActualRepository.countByBankAndBucket(BUCKET_SIZE, null, null)) {
            long[] bars = distribution.computeIfAbsent(row.getBankName(), k -> new long[BUCKET_COUNT]);
            bars[Math.max(0, Math.min(BUCKET_COUNT - 1, row.getBucket()))] += row.getCount();
            sampleSize.merge(row.getBankName(), row.getCount(), Long::sum);
        }
        for (var row : loanActualRepository.summarizeByBank()) {
            avgLimit.put(row.getBankName(), row.getAvgLimit());
            avgRate.put(row.getBankName(), row.getAvgRate());
        }

        if (distribution.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_LOAN_SAMPLE);
        }

        // 2) 내 조건으로 예상 한도를 계산하고, 그 값이 속한 구간을 강조한다.
        long myBase = estimateBase(user);

        List<BankPrediction> banks = new ArrayList<>();
        for (var entry : distribution.entrySet()) {
            String bank = entry.getKey();
            long[] bars = entry.getValue();

            long expected = Math.round(myBase * bankFactor(bank));
            // 금액 구간은 1번부터 시작한다(0번은 반려).
            int highlight = 1 + (int) Math.min(BUCKET_COUNT - 2, expected / BUCKET_SIZE);

            banks.add(new BankPrediction(
                    bank,
                    THEME.getOrDefault(bank, "blue"),
                    Arrays.stream(bars).boxed().toList(),
                    highlight,
                    expected,
                    avgRate.getOrDefault(bank, 3.7),
                    sampleSize.getOrDefault(bank, 0L),
                    "나이 · 직업 · 연봉 기준 산출"));
        }
        // 예상 한도가 큰 순. 화면에서 유리한 은행이 먼저 보이는 편이 낫다.
        banks.sort(Comparator.comparingLong(BankPrediction::expectedLimit).reversed());

        return new LoanPredictionResponse(
                new MyProfile(user.getNickname(), label(user.getAgeRange()), label(user.getJob()),
                        label(user.getSalaryRange()),
                        user.getMaritalStatus() == MaritalStatus.MARRIED ? "기혼" : "미혼"),
                BUCKET_LABELS, banks,
                // 키가 있으면 LLM 이 문장을 다시 쓰고, 없으면 규칙 기반 그대로 나간다.
                reportWriter.write(
                        String.format("%s · %s · 연소득 %s · 거주 %s",
                                label(user.getAgeRange()), label(user.getJob()),
                                label(user.getSalaryRange()),
                                user.getRegion() == null ? "미등록" : user.getRegion()),
                        banks, buildReport(user, banks)));
    }

    /**
     * 막대 하나를 눌렀을 때 — 그 은행·금액대에서 승인받은 사람들의 조건.
     *
     * 그래프만 보면 "몇 명이 받았다" 까지만 알 수 있다. 실제로 궁금한 건
     * "나랑 비슷한 사람이 여기 있나" 다. 그래서 조건을 함께 보여주고,
     * 나와 겹치는 항목(직업·소득·나이대)이 있으면 표시한다.
     */
    public LoanSampleResponse samples(Long userId, String bank, int bucket) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (bucket < 0 || bucket >= BUCKET_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "구간 번호가 올바르지 않습니다.");
        }

        var rows = loanActualRepository.findSamples(bank, BUCKET_SIZE, bucket);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_LOAN_SAMPLE);
        }

        String mySalary = user.getSalaryRange() == null ? null : user.getSalaryRange().name();
        String myJob = user.getJob() == null ? null : user.getJob().name();

        List<Sample> samples = rows.stream()
                .map(r -> new Sample(
                        label(parseAge(r.getAgeRange())),
                        label(parseJob(r.getJob())),
                        label(parseSalary(r.getSalaryRange())),
                        r.getRegion() == null ? "-" : r.getRegion(),
                        r.getActualLimit() == null ? 0 : r.getActualLimit(),
                        r.getActualRate() == null ? 0 : r.getActualRate(),
                        // 소득과 직업이 모두 같으면 "나와 비슷한 조건" 으로 본다.
                        java.util.Objects.equals(r.getSalaryRange(), mySalary)
                                && java.util.Objects.equals(r.getJob(), myJob)))
                .toList();

        // 이 구간에서 가장 흔한 소득 구간이 무엇인지 알려준다.
        String topSalary = rows.stream()
                .map(r -> r.getSalaryRange())
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.groupingBy(s -> s, java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> label(parseSalary(e.getKey())))
                .orElse("정보 없음");

        long myExpected = Math.round(estimateBase(user) * bankFactor(bank));
        int myBucket = 1 + (int) Math.min(BUCKET_COUNT - 2, myExpected / BUCKET_SIZE);
        boolean mineHere = myBucket == bucket;

        boolean rejectedBucket = bucket == REJECTED_BUCKET;
        String summary = rejectedBucket
                ? String.format("반려된 신청은 연소득 %s 구간이 가장 많습니다."
                        + " 소득 증빙과 재직 기간을 먼저 확인해 보세요.", topSalary)
                : String.format("이 구간은 연소득 %s 구간이 가장 많습니다.", topSalary);

        return new LoanSampleResponse(
                bank,
                BUCKET_LABELS.get(bucket),
                rejectedBucket,
                rows.size(),
                summary,
                samples,
                new Mine(mineHere, myExpected,
                        rejectedBucket
                                ? String.format("회원님의 예상 한도는 %s(「%s」 구간)입니다."
                                        + " 아래 조건과 비교해 보세요.",
                                        money(myExpected), BUCKET_LABELS.get(myBucket))
                                : mineHere
                                ? String.format("회원님의 예상 한도(%s)도 이 구간입니다.", money(myExpected))
                                : String.format("회원님의 예상 한도는 %s로 「%s」 구간입니다.",
                                        money(myExpected), BUCKET_LABELS.get(myBucket))));
    }

    private AgeRange parseAge(String v) {
        try { return v == null ? null : AgeRange.valueOf(v); } catch (IllegalArgumentException e) { return null; }
    }

    private Job parseJob(String v) {
        try { return v == null ? null : Job.valueOf(v); } catch (IllegalArgumentException e) { return null; }
    }

    private SalaryRange parseSalary(String v) {
        try { return v == null ? null : SalaryRange.valueOf(v); } catch (IllegalArgumentException e) { return null; }
    }

    /* ── 예상 한도 계산 (결정적) ───────────────────────
       LLM 을 쓰지 않는다. 같은 프로필이면 항상 같은 금액이 나와야 하고,
       "왜 이 금액인가" 를 설명할 수 있어야 한다. */

    private static final Map<SalaryRange, Long> SALARY_BASE = Map.of(
            SalaryRange.UNDER_2000, 70_000_000L,
            SalaryRange.RANGE_2000_3000, 100_000_000L,
            SalaryRange.RANGE_3000_4000, 140_000_000L,
            SalaryRange.RANGE_4000_5000, 175_000_000L,
            SalaryRange.RANGE_5000_7000, 210_000_000L,
            SalaryRange.OVER_7000, 250_000_000L);

    private static final Map<Job, Double> JOB_FACTOR = Map.of(
            Job.EMPLOYEE, 1.00,
            Job.SELF_EMPLOYED, 0.82,
            Job.STUDENT, 0.55,
            Job.JOB_SEEKER, 0.50,
            Job.ETC, 0.75);

    /** 은행별 성향. seed_loan_data.py 의 base 와 같은 값이라 그래프와 예상치가 어긋나지 않는다. */
    private static final Map<String, Double> BANK_FACTOR = Map.of(
            "KB국민은행", 1.00, "우리은행", 1.05, "NH농협은행", 0.92, "하나은행", 1.12);

    private long estimateBase(User user) {
        long base = SALARY_BASE.getOrDefault(user.getSalaryRange(), 100_000_000L);
        double job = JOB_FACTOR.getOrDefault(user.getJob(), 0.8);
        // 30대는 재직 기간이 길어 한도가 조금 더 나온다.
        double age = switch (user.getAgeRange() == null ? AgeRange.AGE_20S_LATE : user.getAgeRange()) {
            case AGE_10S, AGE_20S_EARLY -> 0.88;
            case AGE_20S_LATE -> 1.00;
            case AGE_30S_EARLY, AGE_30S_LATE -> 1.06;
            case AGE_40S_OVER -> 1.02;
        };
        return Math.round(base * job * age);
    }

    private double bankFactor(String bank) {
        return BANK_FACTOR.getOrDefault(bank, 1.0);
    }

    /* ── 분석 리포트 ──────────────────────────────────
       금액·금리·건수는 전부 여기서 계산한다. LLM 이 붙어도 숫자는 코드가 만든 것만 쓴다.
       ------------------------------------------------ */

    /** 0번 칸이 반려다. 나머지 칸의 합이 승인 표본이다. */
    private long approved(BankPrediction b) {
        return b.distribution().stream().skip(1).mapToLong(Long::longValue).sum();
    }

    private double approvalRate(BankPrediction b) {
        long all = b.distribution().stream().mapToLong(Long::longValue).sum();
        return all == 0 ? 0 : approved(b) * 100.0 / all;
    }

    private AnalysisReport buildReport(User user, List<BankPrediction> banks) {
        BankPrediction top = banks.stream()
                .max(Comparator.comparingLong(BankPrediction::expectedLimit)).orElseThrow();
        BankPrediction low = banks.stream()
                .min(Comparator.comparingLong(BankPrediction::expectedLimit)).orElseThrow();
        BankPrediction cheapest = banks.stream()
                .min(Comparator.comparingDouble(BankPrediction::expectedRate)).orElseThrow();
        BankPrediction priciest = banks.stream()
                .max(Comparator.comparingDouble(BankPrediction::expectedRate)).orElseThrow();
        BankPrediction safest = banks.stream()
                .max(Comparator.comparingDouble(this::approvalRate)).orElseThrow();

        long sampleTotal = banks.stream().mapToLong(BankPrediction::sampleSize).sum();
        long approvedTotal = banks.stream().mapToLong(this::approved).sum();
        double approvalPct = sampleTotal == 0 ? 0 : approvedTotal * 100.0 / sampleTotal;

        long spread = top.expectedLimit() - low.expectedLimit();
        double spreadPct = low.expectedLimit() == 0 ? 0 : spread * 100.0 / low.expectedLimit();
        double ratePp = priciest.expectedRate() - cheapest.expectedRate();
        // 금리 차이를 체감 금액으로 바꾼다. 상환 방식을 모르므로 단순이자로만 계산한다.
        long yearlyGap = Math.round(top.expectedLimit() * ratePp / 100.0);

        long topBucketCount = top.distribution().get(top.highlightIndex());
        double topBucketPct = top.sampleSize() == 0 ? 0 : topBucketCount * 100.0 / top.sampleSize();

        List<Metric> metrics = List.of(
                new Metric("예상 한도",
                        money(low.expectedLimit()) + " ~ " + money(top.expectedLimit()),
                        "은행 " + banks.size() + "곳 기준"),
                new Metric("최저 금리", String.format("연 %.2f%%", cheapest.expectedRate()),
                        cheapest.bankName()),
                new Metric("승인율", String.format("%.1f%%", approvalPct),
                        String.format("표본 %,d건 중 %,d건", sampleTotal, approvedTotal)),
                new Metric("은행 간 편차", money(spread),
                        String.format("최저 대비 %.1f%%", spreadPct)));

        List<Section> sections = new ArrayList<>();

        sections.add(new Section("1. 한도 분석", List.of(
                String.format("예상 한도는 %s이 %s으로 가장 높고 %s이 %s으로 가장 낮습니다."
                                + " 편차는 %s으로 최저 대비 %.1f%% 수준입니다.",
                        top.bankName(), money(top.expectedLimit()),
                        low.bankName(), money(low.expectedLimit()), money(spread), spreadPct),
                String.format("%s 기준 귀하의 예상 구간은 「%s」이며, 같은 은행 표본 %,d건 중"
                                + " %,d건(%.0f%%)이 이 구간에 분포합니다.",
                        top.bankName(), BUCKET_LABELS.get(top.highlightIndex()),
                        top.sampleSize(), topBucketCount, topBucketPct),
                "소득과 재직 형태를 같게 두어도 편차는 남습니다."
                        + " 은행마다 소득 인정 방식과 담보 인정 비율이 다르기 때문입니다.")));

        List<String> rate = new ArrayList<>();
        rate.add(String.format("금리는 %s이 연 %.2f%%로 가장 낮고 %s이 연 %.2f%%로 가장 높습니다."
                        + " 스프레드는 %.2f%%p 입니다.",
                cheapest.bankName(), cheapest.expectedRate(),
                priciest.bankName(), priciest.expectedRate(), ratePp));
        rate.add(String.format("%s 같은 금액으로 빌린다고 가정하면 최저·최고 금리의 이자 차이는"
                        + " 연 %s입니다(단순이자 기준, 상환 방식 미반영).",
                object(money(top.expectedLimit())), money(yearlyGap)));
        if (!cheapest.bankName().equals(top.bankName())) {
            rate.add(String.format("한도가 가장 큰 곳(%s)과 금리가 가장 낮은 곳(%s)이 다릅니다."
                            + " 필요 금액이 %s 이하라면 금리를 우선하는 편이 총비용에 유리합니다.",
                    top.bankName(), cheapest.bankName(), money(cheapest.expectedLimit())));
        }
        sections.add(new Section("2. 금리·비용 분석", rate));

        sections.add(new Section("3. 승인 가능성", List.of(
                "은행별 승인율(표본 기준) — " + banks.stream()
                        .map(b -> String.format("%s %.0f%%", b.bankName(), approvalRate(b)))
                        .collect(java.util.stream.Collectors.joining(" · ")),
                String.format("승인율만 보면 %s이 %.0f%%로 가장 높습니다."
                                + " 한도보다 승인 여부가 우선이라면 이 조건부터 확인해 보세요.",
                        safest.bankName(), approvalRate(safest)))));

        List<String> notes = new ArrayList<>();
        if (user.getJob() == Job.SELF_EMPLOYED || user.getJob() == Job.JOB_SEEKER) {
            notes.add("소득 증빙 형태에 따라 인정 소득이 달라지는 조건입니다."
                    + " 소득금액증명원과 건강보험 납부확인서를 미리 준비하면 심사가 빨라집니다.");
        }
        if (user.getMaritalStatus() == MaritalStatus.MARRIED) {
            notes.add("신혼부부 전용 상품의 우대 금리 대상이 될 수 있습니다."
                    + " 이 표본에는 정책 상품 우대가 반영되어 있지 않습니다.");
        }
        notes.add("사전 한도 조회는 신용점수에 영향을 주지 않습니다."
                + " 두 곳 이상에서 같은 시기에 조회하면 조건을 비교할 수 있습니다.");
        sections.add(new Section("4. 조건별 유의점", notes));

        List<String> limitations = List.of(
                "표본은 회원이 자발적으로 입력한 결과입니다. 승인받은 쪽이 더 많이 입력하는 경향이 있어"
                        + " 승인율이 실제보다 높게 나타날 수 있습니다.",
                "신용점수·기존 부채·DSR 은 반영하지 않았습니다. 실제 심사에서는 이 항목이 한도를 좌우합니다.",
                "금리는 표본 시점의 값이며 기준금리 변동과 상품 개편은 반영되지 않았습니다.");

        return new AnalysisReport(
                String.format("예상 한도 %s ~ %s", money(low.expectedLimit()), money(top.expectedLimit())),
                String.format("분석 대상 %s · %s · %s · 연소득 %s  |  표본 %,d건  |  기준일 %s",
                        label(user.getAgeRange()),
                        user.getMaritalStatus() == MaritalStatus.MARRIED ? "기혼" : "미혼",
                        label(user.getJob()), label(user.getSalaryRange()),
                        sampleTotal, java.time.LocalDate.now()),
                metrics,
                sections,
                top.bankName(),
                limitations,
                "본 리포트는 회원이 입력한 대출 결과를 통계적으로 요약한 참고 자료이며,"
                        + " 특정 금융상품에 대한 권유나 자문이 아닙니다. 실제 심사 결과와 다를 수 있습니다.",
                // ⚠️ 키가 있어도 이 문장은 규칙 기반이다. LLM 으로 교체될 때만 true 가 된다.
                false);
    }

    /** 1억 미만은 억으로 쓰면 "0.3억원" 처럼 읽기 나빠진다. 만원으로 바꾼다. */
    private String money(long won) {
        if (won < 100_000_000L) {
            return String.format("%,d만원", Math.round(won / 10_000.0));
        }
        return String.format("%.1f억원", won / 100_000_000.0);
    }

    /** 은행명 뒤 조사. "NH농협은행가" 같은 문장이 나오면 사람이 쓴 글로 안 읽힌다. */
    private String subject(String word) {
        return word + (hasFinalConsonant(word) ? "이" : "가");
    }

    /** 목적격 조사. "4,928만원를" 처럼 어긋나면 문장이 기계가 쓴 티가 난다. */
    private String object(String word) {
        return word + (hasFinalConsonant(word) ? "을" : "를");
    }

    private boolean hasFinalConsonant(String word) {
        char last = word.charAt(word.length() - 1);
        return last >= 0xAC00 && last <= 0xD7A3 && (last - 0xAC00) % 28 != 0;
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
