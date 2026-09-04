package com.zipsa.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.errorCode().status())
                .body(ApiResponse.fail(e.errorCode(), e.getMessage()));
    }

    /** @Valid 검증 실패. 어떤 필드가 왜 틀렸는지 그대로 내려줘야 프론트가 화면에 표시할 수 있다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(this::describe)
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.status())
                .body(ApiResponse.fail(ErrorCode.INVALID_INPUT, detail));
    }

    private String describe(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    /**
     * 잘못된 요청을 400 으로 내린다.
     *
     * <p>이 네 가지는 전부 클라이언트 잘못인데, 잡아주지 않으면 아래 catch-all 로 떨어져
     * 500 INTERNAL_ERROR 가 나간다. 프론트는 재시도해야 할 장애로 오해하고, 운영자는
     * 서버가 터진 줄 알고 로그를 뒤진다. 실제로 {@code GET /api/transactions} 에
     * regionCode 를 빼면 500 이 나가고 있었다.
     */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,  // @RequestParam 필수값 누락
            MethodArgumentTypeMismatchException.class,      // ?page=abc 처럼 타입 불일치
            HttpMessageNotReadableException.class,          // 본문이 없거나 깨진 JSON
            ConstraintViolationException.class              // @Validated 가 붙은 파라미터 검증 실패
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.status())
                .body(ApiResponse.fail(ErrorCode.INVALID_INPUT, badRequestMessage(e)));
    }

    /** 예외 메시지를 그대로 내보내면 내부 타입명이 새어 나간다. 필요한 만큼만 옮긴다. */
    private String badRequestMessage(Exception e) {
        if (e instanceof MissingServletRequestParameterException ex) {
            return ex.getParameterName() + ": 필수 파라미터입니다.";
        }
        if (e instanceof MethodArgumentTypeMismatchException ex) {
            return ex.getName() + ": 값의 형식이 올바르지 않습니다.";
        }
        if (e instanceof HttpMessageNotReadableException) {
            return "요청 본문을 읽을 수 없습니다.";
        }
        return ErrorCode.INVALID_INPUT.defaultMessage();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("처리하지 못한 예외", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage()));
    }
}
