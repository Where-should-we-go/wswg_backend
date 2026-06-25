package com.ssafy.wswg.model.dto;

/** 여행 생성 시 이동수단. 구간별 이동 거리/시간 계산에 사용한다. */
public enum TravelMode {
    CAR,
    TRANSIT;

    public static TravelMode fromNullable(TravelMode mode) {
        return mode == null ? CAR : mode;
    }
}
