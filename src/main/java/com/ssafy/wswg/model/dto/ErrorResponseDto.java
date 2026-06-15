package com.ssafy.wswg.model.dto;

import lombok.Getter;

@Getter
public class ErrorResponseDto {
    private final int code;
    private final int status;
    private final String message;

    public ErrorResponseDto(int code, int status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
}
