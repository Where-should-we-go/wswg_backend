package com.ssafy.wswg.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.wswg.model.dto.NearbyAttractionDto;
import com.ssafy.wswg.model.dto.NearbyAttractionRecommendRequestDto;
import com.ssafy.wswg.model.dto.SemanticAttractionDto;
import com.ssafy.wswg.model.dto.SemanticAttractionRecommendRequestDto;
import com.ssafy.wswg.model.service.AttractionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/attractions")
@RequiredArgsConstructor
public class AttractionController {
    private final AttractionService attractionService;

    @GetMapping("/recommend/nearby")
    public ResponseEntity<List<NearbyAttractionDto>> recommendNearbyAttractions(
            @ModelAttribute NearbyAttractionRecommendRequestDto request) {
        return ResponseEntity.ok(attractionService.recommendNearbyAttractions(request));
    }

    @GetMapping("/recommend/semantic")
    public ResponseEntity<List<SemanticAttractionDto>> recommendSemanticNearbyAttractions(
            @ModelAttribute SemanticAttractionRecommendRequestDto request) {
        return ResponseEntity.ok(attractionService.recommendSemanticNearbyAttractions(request));
    }
}
