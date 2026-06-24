package com.ssafy.wswg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;
import com.ssafy.wswg.model.dto.AiTripPlanCreateRequest;
import com.ssafy.wswg.model.dto.AiTripRecommendationDto;
import com.ssafy.wswg.model.dto.AiTripRecommendationRequest;
import com.ssafy.wswg.model.dto.AiTripRecommendationResponse;
import com.ssafy.wswg.model.dto.TripCreateRequestDto;
import com.ssafy.wswg.model.dto.TripDto;
import com.ssafy.wswg.model.service.AiTripPlanService;
import com.ssafy.wswg.model.service.AiTripRecommendationService;
import com.ssafy.wswg.model.service.TripService;

@ExtendWith(MockitoExtension.class)
class AiTripPlanServiceTest {
    private static final Long USER_ID = 1L;

    @Mock
    AiTripRecommendationService aiTripRecommendationService;

    @Mock
    TripService tripService;

    @InjectMocks
    AiTripPlanService aiTripPlanService;

    @Test
    void createPlan_createsTripWithRecommendationItemsInJsonbData() {
        AiTripRecommendationResponse recommendationResponse = new AiTripRecommendationResponse();
        recommendationResponse.setSessionId("session-1");
        recommendationResponse.setRecommendations(List.of(
                recommendation(1001, "전주 한옥마을", 0.91),
                recommendation(1002, "경기전", 0.82)));
        given(aiTripRecommendationService.recommend(any())).willReturn(recommendationResponse);

        ArgumentCaptor<TripCreateRequestDto> tripRequestCaptor = ArgumentCaptor.forClass(TripCreateRequestDto.class);
        given(tripService.createTrip(eq(USER_ID), tripRequestCaptor.capture())).willAnswer(invocation -> {
            TripCreateRequestDto tripRequest = invocation.getArgument(1);
            TripDto trip = new TripDto();
            trip.setTripId(10L);
            trip.setTitle(tripRequest.getTitle());
            trip.setStartDate(tripRequest.getStartDate());
            trip.setEndDate(tripRequest.getEndDate());
            trip.setGroupId(tripRequest.getGroupId());
            trip.setData(tripRequest.getData());
            return trip;
        });

        AiTripPlanCreateRequest request = new AiTripPlanCreateRequest();
        request.setTitle(" 전주 AI 여행 ");
        request.setStartDate(LocalDate.of(2026, 7, 1));
        request.setEndDate(LocalDate.of(2026, 7, 2));
        request.setGroupId(7L);
        request.setSessionId("session-1");
        request.setSelectedCandidateIds(List.of("c1", "c2"));
        request.setLimit(2);

        TripDto trip = aiTripPlanService.createPlan(USER_ID, request);

        assertThat(trip.getTripId()).isEqualTo(10L);
        assertThat(trip.getTitle()).isEqualTo("전주 AI 여행");
        assertThat(trip.getGroupId()).isEqualTo(7L);

        TripCreateRequestDto tripRequest = tripRequestCaptor.getValue();
        JsonNode items = tripRequest.getData().get("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("content_id").asInt()).isEqualTo(1001);
        assertThat(items.get(0).get("contentId").asInt()).isEqualTo(1001);
        assertThat(items.get(0).get("title").asText()).isEqualTo("전주 한옥마을");
        assertThat(items.get(0).get("visitDate").asText()).isEqualTo("2026-07-01");
        assertThat(items.get(1).get("visitDate").asText()).isEqualTo("2026-07-02");
        assertThat(items.get(0).get("media")).isNotNull();
        assertThat(items.get(0).get("properties").get("source").asText()).isEqualTo("AI_RECOMMENDATION");
        assertThat(tripRequest.getData().get("aiRecommendation").get("sessionId").asText()).isEqualTo("session-1");

        ArgumentCaptor<AiTripRecommendationRequest> recommendationRequestCaptor =
                ArgumentCaptor.forClass(AiTripRecommendationRequest.class);
        org.mockito.Mockito.verify(aiTripRecommendationService).recommend(recommendationRequestCaptor.capture());
        assertThat(recommendationRequestCaptor.getValue().getSelectedCandidateIds()).containsExactly("c1", "c2");
    }

    @Test
    void createPlan_emptyRecommendationThrowsBadRequest() {
        AiTripRecommendationResponse recommendationResponse = new AiTripRecommendationResponse();
        recommendationResponse.setRecommendations(List.of());
        given(aiTripRecommendationService.recommend(any())).willReturn(recommendationResponse);

        AiTripPlanCreateRequest request = new AiTripPlanCreateRequest();
        request.setSessionId("session-1");
        request.setSelectedCandidateIds(List.of("c1"));

        assertThatThrownBy(() -> aiTripPlanService.createPlan(USER_ID, request))
                .isInstanceOf(CommonException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_AI_RECOMMENDATION_REQUEST);
    }

    private AiTripRecommendationDto recommendation(Integer contentId, String title, Double score) {
        AiTripRecommendationDto recommendation = new AiTripRecommendationDto();
        recommendation.setContentId(contentId);
        recommendation.setTitle(title);
        recommendation.setContentTypeId(12);
        recommendation.setSidoCode(35);
        recommendation.setSidoName("전북");
        recommendation.setGugunCode(35010);
        recommendation.setGugunName("전주시");
        recommendation.setLatitude(35.814);
        recommendation.setLongitude(127.153);
        recommendation.setAddr1("전북 전주시 완산구");
        recommendation.setFirstImage1("https://image.example/main.jpg");
        recommendation.setSimilarity(0.9);
        recommendation.setScore(score);
        recommendation.setMatchedCandidateId("c1");
        recommendation.setMatchedCandidateName("전주 한옥마을");
        return recommendation;
    }
}
