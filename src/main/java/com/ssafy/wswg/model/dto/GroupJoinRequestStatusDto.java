package com.ssafy.wswg.model.dto;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GroupJoinRequestStatusDto {
    private Long requestId;
    private Long groupId;
    private Long userId;
    private String name;
    private String email;
    private String profileImageUrl;
    private String status;
    private OffsetDateTime requestedAt;
    private OffsetDateTime resolvedAt;
}
