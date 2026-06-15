package com.ssafy.wswg.model.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class NearbyAttractionDto {
    private Long no;
    private Integer contentId;
    private String title;
    private Integer contentTypeId;
    private Integer sidoCode;
    private Integer gugunCode;
    private String firstImage1;
    private String firstImage2;
    private Double latitude;
    private Double longitude;
    private String addr1;
    private String addr2;
    private Double distanceMeters;
}
