package com.ssafy.wswg.model.dto;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AiTripRecommendationRequest {
    private String sessionId;
    private List<String> selectedCandidateIds;
    private Double latitude;
    private Double longitude;
    private Integer radiusMeters;
    private Integer contentTypeId;
    private Integer limit;
}
