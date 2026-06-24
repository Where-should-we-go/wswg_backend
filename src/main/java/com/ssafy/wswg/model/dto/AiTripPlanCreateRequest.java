package com.ssafy.wswg.model.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AiTripPlanCreateRequest {
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long groupId;
    private String sessionId;
    private List<String> selectedCandidateIds;
    private Double latitude;
    private Double longitude;
    private Integer radiusMeters;
    private Integer contentTypeId;
    private Integer limit;
}
