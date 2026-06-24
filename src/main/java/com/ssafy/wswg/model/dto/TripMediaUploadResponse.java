package com.ssafy.wswg.model.dto;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TripMediaUploadResponse {
    private Long tripId;
    private String itemId;
    private String mediaId;
    private String mediaType;
    private String mediaUrl;
    private JsonNode metadata;
}
