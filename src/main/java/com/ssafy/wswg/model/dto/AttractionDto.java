package com.ssafy.wswg.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AttractionDto {
    private int no;
    private Integer contentId;
    private String title;
    private Integer contentTypeId;
    private Integer sidoCode;
    private Integer gugunCode;
    private String firstImage1;
    private String firstImage2;
    private Integer mapLevel;
    private Double latitude;
    private Double longitude;
    private String tel;
    private String addr1;
    private String addr2;
    private String homepage;
    private String overview;
}