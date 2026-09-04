package com.zipsa.housing;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComplexRepository extends JpaRepository<HousingComplex, Long> {

    /**
     * 지도 마커 — 단지 하나에 마커 하나.
     *
     * <p>V21 이전에는 평형 행을 complex_no 로 GROUP BY 하고 이름·주소를 MAX(),
     * 좌표를 AVG() 로 뽑았다. 단지 정보가 평형마다 복제돼 있어서 대표값을 그렇게밖에
     * 정할 수 없었다. 이제 단지가 그 값을 하나만 가지므로 집계할 것은 임대조건뿐이다.
     */
    @Query(value = """
            SELECT c.complex_no                  AS complexNo,
                   c.name                        AS name,
                   c.road_address                AS roadAddress,
                   c.institution                 AS institution,
                   MIN(u.housing_type)           AS housingType,
                   c.latitude                    AS latitude,
                   c.longitude                   AS longitude,
                   c.household_count             AS householdCount,
                   COUNT(u.id)                   AS styleCount,
                   MIN(u.deposit)                AS minDeposit,
                   MAX(u.deposit)                AS maxDeposit,
                   MIN(u.monthly_rent)           AS minMonthlyRent,
                   MAX(u.monthly_rent)           AS maxMonthlyRent,
                   ROUND(MIN(u.exclusive_area), 2) AS minArea,
                   ROUND(MAX(u.exclusive_area), 2) AS maxArea
            FROM housing_complexes c
            JOIN housing_complex_units u ON u.complex_id = c.id
            WHERE c.region_code = :regionCode
              AND c.latitude IS NOT NULL
              AND (:type IS NULL OR u.housing_type = :type)
            GROUP BY c.id, c.complex_no, c.name, c.road_address, c.institution,
                     c.latitude, c.longitude, c.household_count
            ORDER BY c.household_count DESC NULLS LAST
            """, nativeQuery = true)
    List<ComplexMarker> findMarkers(@Param("regionCode") String regionCode,
                                    @Param("type") String type);

    /** 네이티브 쿼리 결과 컬럼명과 getter 이름이 맞아야 매핑된다. */
    interface ComplexMarker {
        Long getComplexNo();
        String getName();
        String getRoadAddress();
        String getInstitution();
        String getHousingType();
        Double getLatitude();
        Double getLongitude();
        Integer getHouseholdCount();
        Long getStyleCount();
        Long getMinDeposit();
        Long getMaxDeposit();
        Long getMinMonthlyRent();
        Long getMaxMonthlyRent();
        Double getMinArea();
        Double getMaxArea();
    }
}
