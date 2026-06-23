package com.ssafy.wswg.model.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GroupFootprintDto {
    private Integer sidoCode;
    private String sidoName;
    private Integer gugunCode;
    private String gugunName;
    private Integer visitCount;
    private Integer attractionCount;
    private String representativeMediaType;
    private String representativeMediaUrl;
    private Double latitude;
    private Double longitude;
}
