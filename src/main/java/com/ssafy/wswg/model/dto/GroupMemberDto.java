package com.ssafy.wswg.model.dto;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GroupMemberDto {
    private Long userId;
    private String name;
    private String email;
    private String profileImageUrl;
    private Boolean owner;
    private OffsetDateTime joinedAt;
}
