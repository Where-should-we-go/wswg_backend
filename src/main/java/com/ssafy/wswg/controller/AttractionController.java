package com.ssafy.wswg.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.wswg.model.dto.AttractionSearchCondition;
import com.ssafy.wswg.model.dto.AttractionSummaryDto;
import com.ssafy.wswg.model.dto.NearbyAttractionDto;
import com.ssafy.wswg.model.dto.NearbyAttractionRecommendRequestDto;
import com.ssafy.wswg.model.dto.PagedResponse;
import com.ssafy.wswg.model.dto.SemanticAttractionDto;
import com.ssafy.wswg.model.dto.SemanticAttractionRecommendRequestDto;
import com.ssafy.wswg.model.service.AttractionSearchService;
import com.ssafy.wswg.model.service.AttractionService;

import lombok.RequiredArgsConstructor;

/**
 * 관광지 컨트롤러(S3). 검색 목록 + 주변/의미 기반 추천을 제공한다. 인증 필요.
 * contentTypeId는 다중 선택 가능: ?contentTypeId=12&contentTypeId=14.
 */
@RestController
@RequestMapping("/api/attractions")
@RequiredArgsConstructor
public class AttractionController {
    private final AttractionSearchService attractionSearchService;
    private final AttractionService attractionService;

    @GetMapping
    public ResponseEntity<PagedResponse<AttractionSummaryDto>> search(
            @RequestParam(required = false) Integer sidoCode,
            @RequestParam(required = false) Integer gugunCode,
            @RequestParam(required = false) List<Integer> contentTypeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        AttractionSearchCondition cond =
                new AttractionSearchCondition(sidoCode, gugunCode, contentTypeId, keyword);

        return ResponseEntity.ok(attractionSearchService.search(cond, page, size));
    }

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
