package com.zipsa.policy;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PolicyRepository extends JpaRepository<Policy, Long> {

    // null 이 들어갈 수 있는 문자열 파라미터는 cast(... as string) 으로 타입을 못 박는다.
    // 안 하면 PostgreSQL 드라이버가 bytea 로 추론해서 like 비교가 통째로 터진다.
    @Query("""
            select p from Policy p
            where (:keyword  is null or p.title  like concat('%', cast(:keyword as string), '%'))
              and (:region   is null or p.region like concat('%', cast(:region as string), '%'))
              and (:category is null or p.category = :category)
              and (:openOnly = false
                   or ((p.applyStartDate is null or p.applyStartDate <= :today)
                       and (p.applyEndDate is null or p.applyEndDate >= :today)))
            order by p.applyEndDate asc nulls last, p.id desc
            """)
    Page<Policy> search(@Param("keyword") String keyword,
                        @Param("region") String region,
                        @Param("category") PolicyCategory category,
                        @Param("openOnly") boolean openOnly,
                        @Param("today") LocalDate today,
                        Pageable pageable);

    /** 대출 기록에 붙일 대표 정책. 사용자가 정책을 고르지 않았을 때 쓴다. */
    @Query(value = """
            SELECT id FROM policies
            WHERE category = 'LOAN'
            ORDER BY (apply_end_date IS NULL) DESC, apply_end_date DESC
            LIMIT 1
            """, nativeQuery = true)
    java.util.Optional<Long> findRepresentativeLoanPolicyId();

    /**
     * 맞춤 추천 후보. 나이·소득·결혼 조건이 회원과 맞거나, 조건 자체가 없는 정책을 고른다.
     * 점수 계산은 서비스에서 한다 — SQL 로 짜면 왜 추천됐는지 설명(matchReasons)을 만들 수 없다.
     */
    @Query("""
            select p from Policy p
            where (p.applyEndDate is null or p.applyEndDate >= :today)
              and (p.targetMinAge is null or p.targetMinAge <= :age)
              and (p.targetMaxAge is null or p.targetMaxAge >= :age)
            """)
    List<Policy> findRecommendCandidates(@Param("age") int age, @Param("today") LocalDate today);
}
