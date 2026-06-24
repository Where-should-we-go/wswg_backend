package com.ssafy.wswg.model.dto;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlanSocketMessage {
    private String type;
    private Long tripId;
    private Long seq;
    private JsonNode data;
}
