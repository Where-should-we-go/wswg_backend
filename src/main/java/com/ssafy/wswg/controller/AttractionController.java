package com.ssafy.wswg.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.wswg.model.dto.NearbyAttractionDto;
import com.ssafy.wswg.model.service.AttractionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/attractions")
@RequiredArgsConstructor
public class AttractionController {
    private final AttractionService attractionService;

    @GetMapping("/recommend/nearby")
    public ResponseEntity<List<NearbyAttractionDto>> recommendNearbyAttractions(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(required = false) Integer radiusMeters,
            @RequestParam(required = false) Integer contentTypeId,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(attractionService.recommendNearbyAttractions(
                latitude,
                longitude,
                radiusMeters,
                contentTypeId,
                limit));
    }
}
