package com.zipsa.loan;

import com.zipsa.common.ApiResponse;
import com.zipsa.loan.dto.LoanPredictionResponse;
import com.zipsa.loan.dto.LoanRecordRequest;
import com.zipsa.loan.dto.LoanSampleResponse;
import com.zipsa.loan.dto.MyLoanResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 화면 14 「나의 대출 예측하기」. 개인 조건을 쓰므로 로그인 필수. */
@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanPredictionService service;
    private final LoanRecordService recordService;

    public LoanController(LoanPredictionService service, LoanRecordService recordService) {
        this.service = service;
        this.recordService = recordService;
    }

    /** 은행별 한도 분포 + 내 예상 한도 + 분석레포트 */
    @GetMapping("/prediction")
    public ApiResponse<LoanPredictionResponse> predict(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(service.predict(userId));
    }

    /** 막대 클릭 — 그 은행·금액대에서 승인받은 회원들의 조건 */
    @GetMapping("/prediction/samples")
    public ApiResponse<LoanSampleResponse> samples(@AuthenticationPrincipal Long userId,
                                                   @RequestParam String bank,
                                                   @RequestParam int bucket) {
        return ApiResponse.ok(service.samples(userId, bank, bucket));
    }

    /** LOAN-005 — 내 대출 결과 등록 */
    @PostMapping("/actual")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Long>> record(@AuthenticationPrincipal Long userId,
                                                 @Valid @RequestBody LoanRecordRequest request) {
        return ApiResponse.ok(Map.of("loanId", recordService.record(userId, request)));
    }

    /** LOAN-002 — 내가 등록한 대출 */
    @GetMapping("/me")
    public ApiResponse<MyLoanResponse> myLoans(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(recordService.myLoans(userId));
    }

    @DeleteMapping("/actual/{loanId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long userId,
                                    @PathVariable Long loanId) {
        recordService.delete(userId, loanId);
        return ApiResponse.ok();
    }
}
