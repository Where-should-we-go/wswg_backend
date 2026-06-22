package com.ssafy.wswg.model.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TripUpdateRequestDto {
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private JsonNode data;
}
