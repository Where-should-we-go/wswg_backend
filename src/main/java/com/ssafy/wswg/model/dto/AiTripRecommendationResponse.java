package com.ssafy.wswg.model.dto;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AiTripRecommendationResponse {
    private String sessionId;
    private String reply;
    private List<AiTripRecommendationDto> recommendations;
    private String nextQuestion;
}
