package com.ssafy.wswg.model.dto;

import java.time.OffsetDateTime;

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
    private JsonNode actor;
    private String seq;
    private OffsetDateTime ts;
    private JsonNode payload;
}
