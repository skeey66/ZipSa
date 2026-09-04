package com.zipsa.housing;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PublicHousingRepository extends JpaRepository<PublicHousing, Long> {

    /**
     * 모집 상태는 컬럼이 아니라 날짜 비교로 판단한다.
     * 저장해두면 매일 배치로 갱신해야 하고, 안 돌면 화면이 거짓말을 한다.
     */
    // 「전국」 공고는 특정 지역을 골라도 함께 보여준다. 서울을 선택했다고 전국 대상
    // 공고가 목록에서 사라지면 사용자는 그 공고를 영영 못 본다.
    //
    // ⚠️ null 이 들어갈 수 있는 문자열 파라미터는 cast(... as string) 으로 타입을 못 박는다.
    //    안 하면 PostgreSQL 드라이버가 타입을 bytea 로 추론해서
    //    "operator does not exist: character varying ~~ bytea" 로 터진다.
    @Query("""
            select h from PublicHousing h
            where (:region is null or h.region like concat('%', cast(:region as string), '%') or h.region = '전국')
              and (:type    is null or h.housingType = :type)
              and (:keyword is null or h.name   like concat('%', cast(:keyword as string), '%'))
              and (:status  is null
                   or (cast(:status as string) = 'OPEN'     and h.recruitStartDate <= :today and h.recruitEndDate >= :today)
                   or (cast(:status as string) = 'UPCOMING' and h.recruitStartDate >  :today)
                   or (cast(:status as string) = 'CLOSED'   and h.recruitEndDate   <  :today))
            order by h.recruitStartDate desc, h.id desc
            """)
    Page<PublicHousing> search(@Param("region") String region,
                               @Param("type") HousingType type,
                               @Param("keyword") String keyword,
                               @Param("status") String status,
                               @Param("today") LocalDate today,
                               Pageable pageable);

    /** 화면 10 캘린더 — 지정한 달에 모집을 시작하거나 진행 중인 공고. */
    @Query("""
            select h from PublicHousing h
            where h.recruitStartDate <= :monthEnd and h.recruitEndDate >= :monthStart
            order by h.recruitStartDate
            """)
    List<PublicHousing> findForCalendar(@Param("monthStart") LocalDate monthStart,
                                        @Param("monthEnd") LocalDate monthEnd);
}
