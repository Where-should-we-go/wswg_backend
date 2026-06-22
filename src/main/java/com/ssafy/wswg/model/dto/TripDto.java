package com.ssafy.wswg.model.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TripDto {
    private Long tripId;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long userId;
    private Long groupId;
    private String groupName;
    private JsonNode data;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
