package com.ssafy.wswg.model.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MyPageTripResponse {
    private Long tripId;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long userId;
    private Long groupId;
    private String groupName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private TripStatus status;

    public String getStatusLabel() {
        return status == null ? null : status.getLabel();
    }
}
