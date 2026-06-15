package com.ssafy.wswg.tour.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * ldongCode2 응답의 item. 지역(시도/시군구) 마스터.
 * 예) {@code {"rnum":1,"code":"11","name":"서울특별시"}}
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LdongItem {
    private Integer rnum;
    private String code;
    private String name;
}
