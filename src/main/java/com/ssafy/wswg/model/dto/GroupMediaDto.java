package com.ssafy.wswg.model.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GroupMediaDto {
    private Long tripId;
    private String tripTitle;
    private LocalDate visitDate;
    private String blockId;
    private String blockTitle;
    private Integer contentId;
    private String attractionTitle;
    private Integer sidoCode;
    private String sidoName;
    private Integer gugunCode;
    private String gugunName;
    private Double latitude;
    private Double longitude;
    private String mediaType;
    private String mediaUrl;
    private JsonNode metadata;
}
