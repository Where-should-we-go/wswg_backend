package com.ssafy.wswg.model.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PlanEditEvent {
    private String eventId;
    private Long tripId;
    private Long seq;
    private PlanEditType type;
    private Long actorId;
    private String itemId;
    private JsonNode payload;
    private OffsetDateTime createdAt;

    public void prepare(Long tripId, Long actorId, Long seq) {
        this.eventId = UUID.randomUUID().toString();
        this.tripId = tripId;
        this.actorId = actorId;
        this.seq = seq;
        this.createdAt = OffsetDateTime.now();
    }
}
