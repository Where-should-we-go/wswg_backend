package com.ssafy.wswg.model.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GroupMediaRequest {
    private Integer sidoCode;
    private Integer gugunCode;
    private Integer contentId;
    private String mediaType;
}
