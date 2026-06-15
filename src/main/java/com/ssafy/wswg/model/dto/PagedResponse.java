package com.ssafy.wswg.model.dto;

import java.util.List;

import lombok.Getter;
import lombok.ToString;

/**
 * 수동 페이징 응답 봉투.
 * MyBatis라 Spring Data Page가 없으므로 LIMIT/OFFSET 조회 + 별도 COUNT 결과를 조합해 만든다.
 * 기능명세서 §2.2의 응답 계약: content / page / size / totalElements.
 */
@Getter
@ToString
public class PagedResponse<T> {
    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;

    public PagedResponse(List<T> content, int page, int size, long totalElements) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
    }
}
