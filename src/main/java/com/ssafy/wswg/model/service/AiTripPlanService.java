package com.ssafy.wswg.model.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;
import com.ssafy.wswg.model.dto.AiTripPlanCreateRequest;
import com.ssafy.wswg.model.dto.AiTripRecommendationDto;
import com.ssafy.wswg.model.dto.AiTripRecommendationRequest;
import com.ssafy.wswg.model.dto.AiTripRecommendationResponse;
import com.ssafy.wswg.model.dto.TripCreateRequestDto;
import com.ssafy.wswg.model.dto.TripDto;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AiTripPlanService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final String DEFAULT_TITLE = "AI 추천 여행 계획";

    private final AiTripRecommendationService aiTripRecommendationService;
    private final TripService tripService;

    @Transactional
    public TripDto createPlan(Long userId, AiTripPlanCreateRequest request) {
        if (request == null) {
            throw new CommonException(ErrorCode.INVALID_AI_RECOMMENDATION_REQUEST);
        }

        AiTripRecommendationRequest recommendationRequest = toRecommendationRequest(request);
        AiTripRecommendationResponse recommendationResponse = aiTripRecommendationService.recommend(recommendationRequest);
        List<AiTripRecommendationDto> recommendations = recommendationResponse.getRecommendations();
        if (recommendations == null || recommendations.isEmpty()) {
            throw new CommonException(ErrorCode.INVALID_AI_RECOMMENDATION_REQUEST);
        }

        TripCreateRequestDto tripRequest = new TripCreateRequestDto();
        tripRequest.setTitle(normalizeTitle(request.getTitle()));
        tripRequest.setStartDate(request.getStartDate());
        tripRequest.setEndDate(request.getEndDate());
        tripRequest.setGroupId(request.getGroupId());
        tripRequest.setData(buildPlanData(request, recommendations));

        return tripService.createTrip(userId, tripRequest);
    }

    private AiTripRecommendationRequest toRecommendationRequest(AiTripPlanCreateRequest request) {
        AiTripRecommendationRequest recommendationRequest = new AiTripRecommendationRequest();
        recommendationRequest.setSessionId(request.getSessionId());
        recommendationRequest.setSelectedCandidateIds(request.getSelectedCandidateIds());
        recommendationRequest.setLatitude(request.getLatitude());
        recommendationRequest.setLongitude(request.getLongitude());
        recommendationRequest.setRadiusMeters(request.getRadiusMeters());
        recommendationRequest.setContentTypeId(request.getContentTypeId());
        recommendationRequest.setLimit(request.getLimit());
        return recommendationRequest;
    }

    private ObjectNode buildPlanData(
            AiTripPlanCreateRequest request,
            List<AiTripRecommendationDto> recommendations) {
        ObjectNode data = OBJECT_MAPPER.createObjectNode();
        ArrayNode items = data.putArray("items");

        for (int i = 0; i < recommendations.size(); i++) {
            AiTripRecommendationDto recommendation = recommendations.get(i);
            ObjectNode item = items.addObject();
            item.put("id", "ai-place-" + (i + 1));
            putIfNotNull(item, "content_id", recommendation.getContentId());
            putIfNotNull(item, "contentId", recommendation.getContentId());
            putIfNotNull(item, "title", recommendation.getTitle());
            putIfNotNull(item, "contentTypeId", recommendation.getContentTypeId());
            putIfNotNull(item, "sidoCode", recommendation.getSidoCode());
            putIfNotNull(item, "sidoName", recommendation.getSidoName());
            putIfNotNull(item, "gugunCode", recommendation.getGugunCode());
            putIfNotNull(item, "gugunName", recommendation.getGugunName());
            putIfNotNull(item, "lat", recommendation.getLatitude());
            putIfNotNull(item, "lng", recommendation.getLongitude());
            putIfNotNull(item, "addr1", recommendation.getAddr1());
            putIfNotNull(item, "addr2", recommendation.getAddr2());
            putIfNotNull(item, "image", recommendation.getFirstImage1());
            putIfNotNull(item, "thumbnail", recommendation.getFirstImage2());
            item.put("order", i + 1);
            item.put("visitDate", visitDate(request.getStartDate(), request.getEndDate(), i));
            item.putArray("media");

            ObjectNode properties = item.putObject("properties");
            properties.put("source", "AI_RECOMMENDATION");
            putIfNotNull(properties, "score", recommendation.getScore());
            putIfNotNull(properties, "similarity", recommendation.getSimilarity());
            putIfNotNull(properties, "distanceMeters", recommendation.getDistanceMeters());
            putIfNotNull(properties, "matchedCandidateId", recommendation.getMatchedCandidateId());
            putIfNotNull(properties, "matchedCandidateName", recommendation.getMatchedCandidateName());
        }

        ObjectNode aiRecommendation = data.putObject("aiRecommendation");
        aiRecommendation.put("sessionId", request.getSessionId());
        aiRecommendation.putPOJO("selectedCandidateIds", request.getSelectedCandidateIds());
        aiRecommendation.put("createdAt", OffsetDateTime.now().toString());
        if (request.getLatitude() != null && request.getLongitude() != null) {
            aiRecommendation.put("latitude", request.getLatitude());
            aiRecommendation.put("longitude", request.getLongitude());
        }

        return data;
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return DEFAULT_TITLE;
        }

        return title.trim();
    }

    private String visitDate(LocalDate startDate, LocalDate endDate, int index) {
        if (startDate == null) {
            return "";
        }

        if (endDate == null || endDate.isBefore(startDate)) {
            return startDate.toString();
        }

        long days = endDate.toEpochDay() - startDate.toEpochDay() + 1;
        return startDate.plusDays(index % days).toString();
    }

    private void putIfNotNull(ObjectNode node, String fieldName, String value) {
        if (value != null) {
            node.put(fieldName, value);
        }
    }

    private void putIfNotNull(ObjectNode node, String fieldName, Integer value) {
        if (value != null) {
            node.put(fieldName, value);
        }
    }

    private void putIfNotNull(ObjectNode node, String fieldName, Double value) {
        if (value != null) {
            node.put(fieldName, value);
        }
    }
}
