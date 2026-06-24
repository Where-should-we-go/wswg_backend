package com.ssafy.wswg.model.dto;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PlanEditEvent {
    private Long tripId;
    private String type;
    private JsonNode payload;
}
