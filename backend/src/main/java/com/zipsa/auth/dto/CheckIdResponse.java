package com.zipsa.auth.dto;

/**
 * 오퍼레이션 2 (AUTH-004).
 *
 * 안내 문구를 payload 안에 둔다. 새 응답 봉투에는 성공 message 자리가 없고,
 * 이 문구는 오류가 아니라 "사용 가능/불가" 판정 결과를 사람이 읽는 형태로 옮긴 것이라
 * data 에 속하는 값이다.
 */
public record CheckIdResponse(boolean available, String message) {

    public static CheckIdResponse of(boolean available) {
        return new CheckIdResponse(available,
                available ? "사용 가능한 아이디입니다." : "이미 사용 중인 아이디입니다.");
    }
}
