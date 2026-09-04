package com.zipsa.transaction;

import com.zipsa.common.BusinessException;
import com.zipsa.common.ErrorCode;
import com.zipsa.transaction.dto.MapMarkerResponse;
import com.zipsa.transaction.dto.RegionResponse;
import com.zipsa.transaction.dto.TransactionResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TransactionService {

    private final RegionRepository regionRepository;
    private final TransactionRepository transactionRepository;

    public TransactionService(RegionRepository regionRepository,
                              TransactionRepository transactionRepository) {
        this.regionRepository = regionRepository;
        this.transactionRepository = transactionRepository;
    }

    public List<RegionResponse> getRegions() {
        // 지역마다 count 를 날리면 238번 조회한다. 수집된 코드만 한 번에 받아 대조한다.
        Set<String> collected = Set.copyOf(transactionRepository.findRegionCodesWithData());
        return regionRepository.findAllByOrderByRegionCodeAsc().stream()
                .map(r -> RegionResponse.from(r, collected.contains(r.getRegionCode())))
                .toList();
    }

    public List<MapMarkerResponse> getMarkers(String regionCode, DealType dealType, int months) {
        requireRegion(regionCode);
        LocalDate from = LocalDate.now().minusMonths(months);
        return transactionRepository.findMarkers(regionCode, dealType.name(), from).stream()
                .map(MapMarkerResponse::from)
                .toList();
    }

    /**
     * aptName 은 마커 클릭(완전일치), keyword 는 검색창(부분일치).
     * 검색을 프론트에서 거르면 이미 받아온 한 페이지 안에서만 걸려서
     * 실제로는 있는 단지가 「검색 결과 없음」으로 나온다. 그래서 서버에서 찾는다.
     */
    public Page<TransactionResponse> getTransactions(String regionCode, DealType dealType,
                                                     String aptName, String keyword,
                                                     Pageable pageable) {
        requireRegion(regionCode);
        Page<RealEstateTransaction> page;
        if (aptName != null && !aptName.isBlank()) {
            page = transactionRepository
                    .findByApartmentRegionCodeAndDealTypeAndApartmentNameOrderByDealDateDescIdDesc(
                            regionCode, dealType, aptName, pageable);
        } else if (keyword != null && !keyword.isBlank()) {
            page = transactionRepository
                    .findByApartmentRegionCodeAndDealTypeAndApartmentNameContainingOrderByDealDateDescIdDesc(
                            regionCode, dealType, keyword.trim(), pageable);
        } else {
            page = transactionRepository.findByApartmentRegionCodeAndDealTypeOrderByDealDateDescIdDesc(
                    regionCode, dealType, pageable);
        }
        return page.map(TransactionResponse::from);
    }

    /** 없는 지역코드를 조용히 빈 배열로 돌려주면 프론트에서 오타를 못 찾는다. */
    private void requireRegion(String regionCode) {
        if (!regionRepository.existsById(regionCode)) {
            throw new BusinessException(ErrorCode.REGION_NOT_FOUND, "존재하지 않는 지역코드입니다: " + regionCode);
        }
    }
}
