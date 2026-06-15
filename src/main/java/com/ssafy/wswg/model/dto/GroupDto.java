package com.ssafy.wswg.model.dto;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GroupDto {
    private Long groupId;
    private String groupName;
    private Long ownerId;
    private OffsetDateTime createdAt;
    private OffsetDateTime joinedAt;
    private Integer memberCount;
}
