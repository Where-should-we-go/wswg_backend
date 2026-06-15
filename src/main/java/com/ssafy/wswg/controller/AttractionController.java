package com.ssafy.wswg.controller;

import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.wswg.model.dto.AttractionDetailDto;
import com.ssafy.wswg.model.dto.AttractionSearchRequest;
import com.ssafy.wswg.model.dto.AttractionSummaryDto;
import com.ssafy.wswg.model.dto.NearbyAttractionDto;
import com.ssafy.wswg.model.dto.NearbyAttractionRecommendRequestDto;
import com.ssafy.wswg.model.dto.PagedResponse;
import com.ssafy.wswg.model.dto.SemanticAttractionDto;
import com.ssafy.wswg.model.dto.SemanticAttractionRecommendRequestDto;
import com.ssafy.wswg.model.service.AttractionDetailService;
import com.ssafy.wswg.model.service.AttractionSearchService;
import com.ssafy.wswg.model.service.AttractionService;

import lombok.RequiredArgsConstructor;

/**
 * 관광지 컨트롤러. 검색 목록(S3)·단건 상세(S4)·주변/의미 기반 추천을 제공한다. 인증 필요.
 * contentTypeId는 다중 선택 가능: ?contentTypeId=12&contentTypeId=14.
 */
@RestController
@RequestMapping("/api/attractions")
@RequiredArgsConstructor
public class AttractionController {
    private final AttractionSearchService attractionSearchService;
    private final AttractionDetailService attractionDetailService;
    private final AttractionService attractionService;

    @GetMapping
    public ResponseEntity<PagedResponse<AttractionSummaryDto>> search(
            @ParameterObject AttractionSearchRequest request) {
        return ResponseEntity.ok(attractionSearchService.search(
                request.toCondition(), request.getPage(), request.getSize()));
    }

    /**
     * 관광지 단건 상세(S4 · FR-TOUR-03). 없는 contentId면 404(NOT_FOUND_ATTRACTION).
     * 비어 있는 overview/홈페이지·휴무일은 서비스가 TourAPI write-through로 채운다.
     */
    @GetMapping("/{contentId}")
    public ResponseEntity<AttractionDetailDto> getDetail(@PathVariable Integer contentId) {
        return ResponseEntity.ok(attractionDetailService.getDetail(contentId));
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
