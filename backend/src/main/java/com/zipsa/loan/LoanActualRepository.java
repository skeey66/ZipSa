package com.zipsa.loan;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanActualRepository extends JpaRepository<LoanActual, Long> {

    List<LoanActual> findByUserIdOrderByIdDesc(Long userId);

    /** 커뮤니티 뱃지 — 회원별 등록 은행 목록. 게시글 목록에서 한 번에 가져온다. */
    @Query(value = """
            SELECT DISTINCT user_id AS userId, bank_name AS bankName
            FROM loan_actuals
            WHERE user_id IN (:userIds)
            """, nativeQuery = true)
    List<UserBankRow> findBanksOfUsers(@Param("userIds") List<Long> userIds);

    interface UserBankRow {
        Long getUserId();
        String getBankName();
    }


    /**
     * 은행 × 한도구간별 건수. 화면 14 막대그래프의 원천이다.
     *
     * 구간은 SQL 에서 나눈다. 전부 읽어와 자바에서 세면 표본이 커질수록 느려지고,
     * 어차피 화면에 필요한 건 개수뿐이다.
     */
    @Query(value = """
            SELECT bank_name AS bankName,
                   CASE WHEN status = 'REJECTED' THEN 0
                        ELSE 1 + LEAST(4, FLOOR(actual_limit / :bucketSize))::int
                   END AS bucket,
                   COUNT(*)                 AS count,
                   ROUND(AVG(actual_limit)) AS avgLimit,
                   ROUND(AVG(actual_rate)::numeric, 2) AS avgRate
            FROM loan_actuals
            WHERE (:job    IS NULL OR EXISTS (
                     SELECT 1 FROM users u WHERE u.id = loan_actuals.user_id AND u.job = :job))
              AND (:salary IS NULL OR EXISTS (
                     SELECT 1 FROM users u WHERE u.id = loan_actuals.user_id AND u.salary_range = :salary))
            GROUP BY bank_name, bucket
            ORDER BY bank_name, bucket
            """, nativeQuery = true)
    List<BucketRow> countByBankAndBucket(@Param("bucketSize") long bucketSize,
                                         @Param("job") String job,
                                         @Param("salary") String salary);

    /** 은행별 표본 요약. 팝업(14b)의 예상 금액·금리에 쓴다. */
    @Query(value = """
            SELECT bank_name AS bankName,
                   COUNT(*) AS count,
                   ROUND(AVG(actual_limit) FILTER (WHERE status = 'APPROVED')) AS avgLimit,
                   ROUND(AVG(actual_rate) FILTER (WHERE status = 'APPROVED')::numeric, 2) AS avgRate,
                   MAX(actual_limit) AS maxLimit,
                   COUNT(*) FILTER (WHERE status = 'REJECTED') AS rejectedCount
            FROM loan_actuals
            GROUP BY bank_name
            ORDER BY AVG(actual_limit) FILTER (WHERE status = 'APPROVED') DESC
            """, nativeQuery = true)
    List<BankSummaryRow> summarizeByBank();

    /**
     * 특정 은행·금액대에서 승인받은 회원들의 조건.
     * 채용 사이트의 「합격자 스펙」 처럼 "나와 비슷한 사람이 얼마를 받았나" 를 보여준다.
     *
     * ⚠️ 닉네임·아이디는 뽑지 않는다. 대출 한도는 소득을 역산할 수 있는 민감 정보라
     *    구간형 프로필(나이대·직업·소득구간·지역)까지만 노출한다.
     */
    @Query(value = """
            SELECT u.age_range    AS ageRange,
                   u.job          AS job,
                   u.salary_range AS salaryRange,
                   u.region       AS region,
                   la.actual_limit AS actualLimit,
                   la.actual_rate  AS actualRate,
                   la.status       AS status
            FROM loan_actuals la
            JOIN users u ON u.id = la.user_id
            WHERE la.bank_name = :bank
              AND (CASE WHEN la.status = 'REJECTED' THEN 0
                        ELSE 1 + LEAST(4, FLOOR(la.actual_limit / :bucketSize))::int
                   END) = :bucket
            ORDER BY la.actual_limit DESC NULLS LAST
            """, nativeQuery = true)
    List<SampleRow> findSamples(@Param("bank") String bank,
                                @Param("bucketSize") long bucketSize,
                                @Param("bucket") int bucket);

    interface SampleRow {
        String getAgeRange();
        String getJob();
        String getSalaryRange();
        String getRegion();
        Long getActualLimit();
        Double getActualRate();
        String getStatus();
    }

    interface BucketRow {
        String getBankName();
        Integer getBucket();
        Long getCount();
        Long getAvgLimit();
        Double getAvgRate();
    }

    interface BankSummaryRow {
        String getBankName();
        Long getCount();
        Long getAvgLimit();
        Double getAvgRate();
        Long getMaxLimit();
        Long getRejectedCount();
    }
}
