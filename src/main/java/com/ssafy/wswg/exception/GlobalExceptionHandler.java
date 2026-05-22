package com.ssafy.wswg.exception;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import lombok.extern.slf4j.Slf4j;

@ControllerAdvice // @RestControllerAdvice 아님!
@Slf4j
public class GlobalExceptionHandler {

    // 모든 일반 예외 처리
    @ExceptionHandler(Exception.class)
    public String handleAll(Exception e, Model model) {
        log.error("Internal Server Error", e);
        model.addAttribute("exception", e); // 에러 객체 자체를 넘겨서 상세 분석 가능
        return "error/500"; // src/main/resources/templates/error/500.html 호출
    }
}