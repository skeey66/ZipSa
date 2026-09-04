package com.zipsa.housing;

import com.zipsa.common.ApiResponse;
import com.zipsa.housing.dto.ComplexMarkerResponse;
import com.zipsa.housing.dto.ComplexUnitResponse;
import com.zipsa.housing.dto.NoticeResponse;
import java.time.YearMonth;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

/** 화면 09 매물 랜딩 · 10 공공임대. 전부 비로그인 조회 가능. */
@RestController
@RequestMapping("/api/public-housings")
public class HousingController {

    private final HousingService service;

    public HousingController(HousingService service) {
        this.service = service;
    }

    /** HOUSING-001 — 모집 공고 목록 */
    @GetMapping
    public ApiResponse<Page<NoticeResponse>> getNotices(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) HousingType housingType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String recruitStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.getNotices(region, housingType, keyword, recruitStatus,
                PageRequest.of(page, size)));
    }

    /** 화면 10 캘린더 — 한 달치 공고 */
    @GetMapping("/calendar")
    public ApiResponse<List<NoticeResponse>> getCalendar(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return ApiResponse.ok(service.getCalendar(month));
    }

    /** 지도 마커 — 단지 단위 집계 */
    @GetMapping("/map")
    public ApiResponse<List<ComplexMarkerResponse>> getMarkers(
            @RequestParam String regionCode,
            @RequestParam(required = false) HousingType housingType) {
        return ApiResponse.ok(service.getMarkers(regionCode, housingType));
    }

    /** HOUSING-002 — 단지 상세(평형별 임대조건) */
    @GetMapping("/complexes/{complexNo}")
    public ApiResponse<List<ComplexUnitResponse>> getUnits(@PathVariable Long complexNo) {
        return ApiResponse.ok(service.getUnits(complexNo));
    }
}
