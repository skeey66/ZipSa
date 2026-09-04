package com.zipsa.transaction;

import com.zipsa.common.ApiResponse;
import com.zipsa.transaction.dto.MapMarkerResponse;
import com.zipsa.transaction.dto.RegionResponse;
import com.zipsa.transaction.dto.TransactionResponse;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

/** 화면 11 실거래가 지도. 전부 비로그인 조회 가능. */
@RestController
@RequestMapping("/api")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    /** 오퍼레이션 36 — 지역 목록 */
    @GetMapping("/regions")
    public ApiResponse<List<RegionResponse>> getRegions() {
        return ApiResponse.ok(service.getRegions());
    }

    /** 오퍼레이션 37 — 지도 마커 (아파트 단위 집계) */
    @GetMapping("/transactions/map")
    public ApiResponse<List<MapMarkerResponse>> getMarkers(
            @RequestParam String regionCode,
            @RequestParam(defaultValue = "SALE") DealType dealType,
            @RequestParam(defaultValue = "12") int months) {
        return ApiResponse.ok(service.getMarkers(regionCode, dealType, months));
    }

    /** 오퍼레이션 38 — 실거래 목록 */
    @GetMapping("/transactions")
    public ApiResponse<Page<TransactionResponse>> getTransactions(
            @RequestParam String regionCode,
            @RequestParam(defaultValue = "SALE") DealType dealType,
            @RequestParam(required = false) String aptName,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.getTransactions(regionCode, dealType, aptName, keyword,
                        PageRequest.of(page, size)));
    }
}
