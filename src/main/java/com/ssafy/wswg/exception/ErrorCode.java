package com.ssafy.wswg.exception;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    // 400
    INVALID_GROUP_NAME(40000, HttpStatus.BAD_REQUEST, "모임 이름은 1자 이상 100자 이하로 입력해야 합니다."),
    INVALID_INVITE_TOKEN(40001, HttpStatus.BAD_REQUEST, "유효하지 않은 초대 토큰입니다."),
    EXPIRED_INVITE_TOKEN(40002, HttpStatus.BAD_REQUEST, "만료된 초대 링크입니다."),
    BAD_REQUEST_JSON(40003, HttpStatus.BAD_REQUEST, "잘못된 JSON 형식입니다."),

    // 401
    UNAUTHORIZED(40100, HttpStatus.UNAUTHORIZED, "인증되지 않은 사용자입니다."),

    // 403
    GROUP_OWNER_REQUIRED(40300, HttpStatus.FORBIDDEN, "모임장만 처리할 수 있습니다."),
    GROUP_MEMBER_REQUIRED(40301, HttpStatus.FORBIDDEN, "모임 멤버만 조회할 수 있습니다."),

    // 404
    NOT_FOUND_GROUP(40400, HttpStatus.NOT_FOUND, "존재하지 않는 모임입니다."),
    NOT_FOUND_USER(40401, HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),

    // 500
    INTERNAL_SERVER_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류입니다.");

    private final Integer code;
    private final HttpStatus httpStatus;
    private final String message;
}
