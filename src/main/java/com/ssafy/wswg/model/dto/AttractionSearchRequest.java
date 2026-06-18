package com.ssafy.wswg.model.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 관광지 검색 요청 파라미터(웹 계층). GET 쿼리 파라미터를 @ModelAttribute로 바인딩한다.
 * contentTypeId는 다중 선택 가능: ?contentTypeId=12&contentTypeId=14.
 * page/size 기본값은 필드 초기화로 둔다(미지정 시 page=0, size=12).
 *
 * <p>페이징(page/size)은 영속 필터 조건과 분리한다 — COUNT 쿼리는 페이징과 무관해야 하므로
 * {@link #toCondition()}으로 필터만 추려 DAO에 넘기고 page/size는 서비스에 따로 전달한다.
 */
@Getter
@Setter
@ToString
public class AttractionSearchRequest {
    private Integer sidoCode;
    private Integer gugunCode;
    private List<Integer> contentTypeId;
    private String keyword;
    private int page = 0;
    private int size = 12;

    /** 필터 조건만 추려 검색 condition으로 변환(page/size는 서비스에 별도 전달). */
    public AttractionSearchCondition toCondition() {
        return new AttractionSearchCondition(sidoCode, gugunCode, contentTypeId, keyword);
    }
}
