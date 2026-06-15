package com.ssafy.wswg.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 관광지 검색 결과 카드용 요약 DTO.
 * 상세 화면(A-6)이 쓰는 전체 필드(overview/tel/addr 등) 대신 목록 표시에 필요한 것만 담는다.
 * sidoName/gugunName은 sidos/guguns LEFT JOIN으로 채운다(코드가 null인 관광지면 null).
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AttractionSummaryDto {
    private Integer contentId;
    private String title;
    private String sidoName;
    private String gugunName;
    private Integer contentTypeId;
    private String firstImage1;
}
