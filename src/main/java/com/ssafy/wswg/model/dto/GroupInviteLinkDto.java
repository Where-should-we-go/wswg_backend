package com.ssafy.wswg.model.dto;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GroupInviteLinkDto {
    private Long inviteId;
    private Long groupId;
    private String token;
    private String url;
    private Long createdBy;
    private OffsetDateTime expiresAt;
    private OffsetDateTime createdAt;
}
