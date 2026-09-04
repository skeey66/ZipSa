package com.zipsa.transaction;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<RealEstateTransaction, Long> {

    /**
     * 지도 마커용 집계.
     * 같은 아파트의 거래가 수백 건이라 그대로 내려보내면 마커가 겹쳐 아무것도 안 보인다.
     * 아파트 단위로 묶어서 대표값만 준다.
     *
     * <p>V20 이전에는 좌표를 AVG() 로 평균 냈다. 거래 행마다 좌표 사본이 있어서
     * 그러지 않으면 대표값을 정할 수 없었기 때문이다. 이제 단지가 좌표를 하나만
     * 가지므로 그냥 집어 오면 된다.
     */
    @Query(value = """
            SELECT a.name                        AS aptName,
                   a.latitude                    AS latitude,
                   a.longitude                   AS longitude,
                   COUNT(*)                      AS dealCount,
                   ROUND(AVG(t.deal_amount))     AS avgAmount,
                   MAX(t.deal_amount)            AS maxAmount,
                   MIN(t.deal_amount)            AS minAmount,
                   ROUND(AVG(t.exclusive_area), 2) AS avgArea,
                   MAX(t.deal_date)              AS lastDealDate
            FROM real_estate_transactions t
            JOIN apartments a ON a.id = t.apartment_id
            WHERE a.region_code = :regionCode
              AND t.deal_type   = :dealType
              AND t.deal_date  >= :from
              AND a.latitude IS NOT NULL
            GROUP BY a.id, a.name, a.latitude, a.longitude
            ORDER BY COUNT(*) DESC
            """, nativeQuery = true)
    List<MapMarker> findMarkers(@Param("regionCode") String regionCode,
                                @Param("dealType") String dealType,
                                @Param("from") LocalDate from);

    // 정렬에 id 를 덧붙인다. 같은 날 거래가 수십 건이라 deal_date 만으로는 순서가 흔들리고,
    // 페이지를 넘길 때 같은 행이 또 나오거나 누락된다.
    //
    // @EntityGraph 가 없으면 목록 50건마다 단지를 한 번씩 더 조회한다(N+1).
    @EntityGraph(attributePaths = "apartment")
    Page<RealEstateTransaction> findByApartmentRegionCodeAndDealTypeOrderByDealDateDescIdDesc(
            String regionCode, DealType dealType, Pageable pageable);

    /** 마커 클릭 — 단지명 완전일치. 「현대」가 「현대아이파크」까지 끌고 오면 안 된다. */
    @EntityGraph(attributePaths = "apartment")
    Page<RealEstateTransaction> findByApartmentRegionCodeAndDealTypeAndApartmentNameOrderByDealDateDescIdDesc(
            String regionCode, DealType dealType, String aptName, Pageable pageable);

    /** 검색창 — 단지명 부분일치. */
    @EntityGraph(attributePaths = "apartment")
    Page<RealEstateTransaction> findByApartmentRegionCodeAndDealTypeAndApartmentNameContainingOrderByDealDateDescIdDesc(
            String regionCode, DealType dealType, String keyword, Pageable pageable);

    /**
     * 실거래가 한 건이라도 쌓인 지역코드. 지역 목록에 「수집됨」 표시를 붙이는 데 쓴다.
     * 단지만 만들어지고 거래가 없는 경우가 있을 수 있어 거래 쪽에서 거슬러 센다.
     */
    @Query("select distinct t.apartment.regionCode from RealEstateTransaction t")
    List<String> findRegionCodesWithData();

    /** 인터페이스 프로젝션. 네이티브 쿼리 결과 컬럼명과 getter 이름이 맞아야 매핑된다. */
    interface MapMarker {
        String getAptName();
        Double getLatitude();
        Double getLongitude();
        Long getDealCount();
        Long getAvgAmount();
        Long getMaxAmount();
        Long getMinAmount();
        Double getAvgArea();
        LocalDate getLastDealDate();
    }
}
