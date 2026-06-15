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
    INVALID_NEARBY_RECOMMEND_REQUEST(40004, HttpStatus.BAD_REQUEST, "주변 명소 추천 요청 파라미터를 확인해 주세요."),
    INVALID_SEMANTIC_RECOMMEND_REQUEST(40005, HttpStatus.BAD_REQUEST, "의미 기반 명소 추천 요청 파라미터를 확인해 주세요."),
    INVALID_PAGINATION(40006, HttpStatus.BAD_REQUEST, "page는 0 이상, size는 1 이상이어야 합니다."),

    // 401
    UNAUTHORIZED(40100, HttpStatus.UNAUTHORIZED, "인증되지 않은 사용자입니다."),

    // 403
    GROUP_OWNER_REQUIRED(40300, HttpStatus.FORBIDDEN, "모임장만 처리할 수 있습니다."),
    GROUP_MEMBER_REQUIRED(40301, HttpStatus.FORBIDDEN, "모임 멤버만 조회할 수 있습니다."),

    // 404
    NOT_FOUND_GROUP(40400, HttpStatus.NOT_FOUND, "존재하지 않는 모임입니다."),
    NOT_FOUND_USER(40401, HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    NOT_FOUND_ATTRACTION(40402, HttpStatus.NOT_FOUND, "존재하지 않는 관광지입니다."),

    // 409
    TOUR_LOAD_ALREADY_RUNNING(40900, HttpStatus.CONFLICT, "관광정보 적재가 이미 진행 중입니다."),

    // 500
    INTERNAL_SERVER_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류입니다."),

    // 502 (TourAPI 연동 실패)
    TOUR_API_KEY_INVALID(50200, HttpStatus.BAD_GATEWAY, "TourAPI 서비스키가 유효하지 않습니다."),
    TOUR_API_QUOTA_EXCEEDED(50201, HttpStatus.BAD_GATEWAY, "TourAPI 일일 트래픽을 초과했습니다."),
    TOUR_LOAD_FAILED(50202, HttpStatus.BAD_GATEWAY, "관광정보 적재에 실패했습니다."),
    EMBEDDING_REQUEST_FAILED(50203, HttpStatus.BAD_GATEWAY, "임베딩 생성에 실패했습니다."),
    TOUR_API_DETAIL_FAILED(50204, HttpStatus.BAD_GATEWAY, "관광지 상세 정보를 불러오지 못했습니다.");

    private final Integer code;
    private final HttpStatus httpStatus;
    private final String message;
}
