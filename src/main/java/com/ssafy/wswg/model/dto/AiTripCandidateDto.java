package com.ssafy.wswg.model.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AiTripCandidateDto {
    private String candidateId;
    private String name;
    private String regionHint;
    private String description;
    private String reason;
}
