package com.ssafy.wswg.model.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SemanticAttractionRecommendRequestDto {
    private Double latitude;
    private Double longitude;
    private String query;
    private Integer radiusMeters;
    private Integer contentTypeId;
    private Integer limit;
}
