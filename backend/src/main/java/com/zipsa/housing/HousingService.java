package com.zipsa.housing;

import com.zipsa.common.BusinessException;
import com.zipsa.common.ErrorCode;
import com.zipsa.housing.dto.ComplexMarkerResponse;
import com.zipsa.housing.dto.ComplexUnitResponse;
import com.zipsa.housing.dto.NoticeResponse;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class HousingService {

    private final PublicHousingRepository noticeRepository;
    private final ComplexRepository complexRepository;
    private final ComplexUnitRepository complexUnitRepository;

    public HousingService(PublicHousingRepository noticeRepository,
                          ComplexRepository complexRepository,
                          ComplexUnitRepository complexUnitRepository) {
        this.noticeRepository = noticeRepository;
        this.complexRepository = complexRepository;
        this.complexUnitRepository = complexUnitRepository;
    }

    /** HOUSING-001 — 모집 공고 목록 */
    public Page<NoticeResponse> getNotices(String region, HousingType type, String keyword,
                                           String status, Pageable pageable) {
        LocalDate today = LocalDate.now();
        return noticeRepository
                .search(blankToNull(region), type, blankToNull(keyword), blankToNull(status),
                        today, pageable)
                .map(h -> NoticeResponse.from(h, today));
    }

    /** 화면 10 캘린더 — 한 달치 공고. 점 표시와 날짜 클릭 목록에 함께 쓴다. */
    public List<NoticeResponse> getCalendar(YearMonth month) {
        LocalDate today = LocalDate.now();
        return noticeRepository
                .findForCalendar(month.atDay(1), month.atEndOfMonth())
                .stream()
                .map(h -> NoticeResponse.from(h, today))
                .toList();
    }

    /** 지도 마커 — 단지 단위 집계 */
    public List<ComplexMarkerResponse> getMarkers(String regionCode, HousingType type) {
        if (regionCode == null || regionCode.length() != 5) {
            throw new BusinessException(ErrorCode.REGION_NOT_FOUND,
                    "지역코드는 법정동코드 5자리여야 합니다: " + regionCode);
        }
        // V21 부터 단지가 법정동코드 5자리를 그대로 갖는다. 쪼갤 필요가 없다.
        return complexRepository
                .findMarkers(regionCode, type == null ? null : type.name())
                .stream()
                .map(ComplexMarkerResponse::from)
                .toList();
    }

    /** HOUSING-002 — 단지 상세(평형별 임대조건) */
    public List<ComplexUnitResponse> getUnits(Long complexNo) {
        List<ComplexUnitResponse> units = complexUnitRepository.findByComplexNo(complexNo)
                .stream()
                .map(ComplexUnitResponse::from)
                .toList();
        if (units.isEmpty()) {
            throw new BusinessException(ErrorCode.PUBLIC_HOUSING_NOT_FOUND);
        }
        return units;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
