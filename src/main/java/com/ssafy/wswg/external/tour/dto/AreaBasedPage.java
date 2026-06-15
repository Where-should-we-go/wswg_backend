package com.ssafy.wswg.external.tour.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * areaBasedList2 한 페이지 조회 결과.
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AreaBasedPage {
    private List<AreaBasedItem> items;
    private int totalCount;
    private int pageNo;
    private int numOfRows;
}
