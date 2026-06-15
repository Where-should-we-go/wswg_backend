package com.ssafy.wswg.model.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class NearbyAttractionRecommendRequestDto {
    private Double latitude;
    private Double longitude;
    private Integer radiusMeters;
    private Integer contentTypeId;
    private Integer limit;
}
