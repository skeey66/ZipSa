package com.zipsa.common;

/**
 * 모든 API 응답을 감싸는 공통 봉투.
 * 형식은 REST API 명세서 v4 「1.1 공통 응답 포맷」과 1:1로 일치한다.
 *
 *   성공: { "success": true,  "data": {...}, "error": null }
 *   실패: { "success": false, "data": null,  "error": { "code": "...", "message": "..." } }
 *
 * 성공과 실패 중 한쪽만 채운다. 둘 다 값이 있는 응답은 만들지 않는다.
 * null 을 생략하지 않는 이유: 프론트가 키 존재 여부로 분기하지 않도록 형태를 고정한다.
 */
public record ApiResponse<T>(boolean success, T data, ApiError error) {

    public record ApiError(String code, String message) {
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, null, new ApiError(errorCode.name(), message));
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode) {
        return fail(errorCode, errorCode.defaultMessage());
    }
}
