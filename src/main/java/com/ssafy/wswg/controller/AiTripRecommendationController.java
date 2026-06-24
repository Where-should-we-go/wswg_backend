package com.ssafy.wswg.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.wswg.model.dto.AiTripCandidateRequest;
import com.ssafy.wswg.model.dto.AiTripCandidateResponse;
import com.ssafy.wswg.model.dto.AiTripPlanCreateRequest;
import com.ssafy.wswg.model.dto.AiTripRecommendationRequest;
import com.ssafy.wswg.model.dto.AiTripRecommendationResponse;
import com.ssafy.wswg.model.dto.TripDto;
import com.ssafy.wswg.model.service.AiTripPlanService;
import com.ssafy.wswg.model.service.AiTripRecommendationService;
import com.ssafy.wswg.security.LoginUserId;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiTripRecommendationController {
    private final AiTripRecommendationService aiTripRecommendationService;
    private final AiTripPlanService aiTripPlanService;

    @PostMapping("/trip-candidates")
    public ResponseEntity<AiTripCandidateResponse> createCandidates(
            @RequestBody(required = false) AiTripCandidateRequest request) {
        return ResponseEntity.ok(aiTripRecommendationService.createCandidates(request));
    }

    @PostMapping("/trip-recommendations")
    public ResponseEntity<AiTripRecommendationResponse> recommend(
            @RequestBody(required = false) AiTripRecommendationRequest request) {
        return ResponseEntity.ok(aiTripRecommendationService.recommend(request));
    }

    @PostMapping("/trip-plans")
    public ResponseEntity<TripDto> createPlan(
            @LoginUserId Long userId,
            @RequestBody(required = false) AiTripPlanCreateRequest request) {
        TripDto trip = aiTripPlanService.createPlan(userId, request);

        return ResponseEntity
                .created(URI.create("/api/trips/" + trip.getTripId()))
                .body(trip);
    }
}
