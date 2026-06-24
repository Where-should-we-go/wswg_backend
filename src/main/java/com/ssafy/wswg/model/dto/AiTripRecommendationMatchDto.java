package com.ssafy.wswg.model.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AiTripRecommendationMatchDto {
    private Long no;
    private Integer contentId;
    private String title;
    private Integer contentTypeId;
    private Integer sidoCode;
    private String sidoName;
    private Integer gugunCode;
    private String gugunName;
    private String firstImage1;
    private String firstImage2;
    private Double latitude;
    private Double longitude;
    private String addr1;
    private String addr2;
    private Double distanceMeters;
    private Double similarity;
}
