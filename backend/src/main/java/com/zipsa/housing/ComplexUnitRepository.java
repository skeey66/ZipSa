package com.zipsa.housing;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 평형 조회.
 *
 * <p>단지(ComplexRepository)와 나눠 둔다. 한 리포지토리가 자기 도메인 타입이 아닌
 * 엔티티를 반환하면 Spring Data 가 DTO 프로젝션으로 오해해서
 * {@code select new HousingComplexUnit()} 같은 쿼리를 만든다.
 */
public interface ComplexUnitRepository extends JpaRepository<HousingComplexUnit, Long> {

    /** 단지 상세 — 평형을 면적 오름차순으로. 단지도 함께 가져와 N+1 을 막는다. */
    @EntityGraph(attributePaths = "complex")
    @Query("select u from HousingComplexUnit u where u.complex.complexNo = :complexNo "
            + "order by u.exclusiveArea asc nulls last")
    List<HousingComplexUnit> findByComplexNo(@Param("complexNo") Long complexNo);
}
