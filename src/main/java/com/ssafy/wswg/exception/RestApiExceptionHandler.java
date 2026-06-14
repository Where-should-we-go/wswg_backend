package com.ssafy.wswg.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ssafy.wswg.model.dto.ErrorResponseDto;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(annotations = RestController.class)
public class RestApiExceptionHandler {

    @ExceptionHandler(CommonException.class)
    public ResponseEntity<ErrorResponseDto> handleCommonException(CommonException exception) {
        ErrorCode errorCode = exception.getErrorCode();

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(new ErrorResponseDto(
                        errorCode.getCode(),
                        errorCode.getHttpStatus().value(),
                        errorCode.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDto> handleHttpMessageNotReadable() {
        ErrorCode errorCode = ErrorCode.BAD_REQUEST_JSON;

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(new ErrorResponseDto(
                        errorCode.getCode(),
                        errorCode.getHttpStatus().value(),
                        errorCode.getMessage()));
    }
}
