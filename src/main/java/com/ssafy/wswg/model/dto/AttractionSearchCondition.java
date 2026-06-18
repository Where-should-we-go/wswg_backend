package com.ssafy.wswg.model.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 관광지 검색 필터 조건. 모든 필드 선택적(null/빈값이면 해당 조건 미적용).
 * 페이징(page/size)은 이 조건이 아니라 서비스 계층에서 limit/offset으로 변환해 DAO에 따로 넘긴다
 * (COUNT 쿼리는 페이징과 무관해야 하므로 필터만 담는다).
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AttractionSearchCondition {
    private Integer sidoCode;
    private Integer gugunCode;
    private List<Integer> contentTypeIds;
    private String keyword;
}
