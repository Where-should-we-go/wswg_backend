package com.ssafy.wswg;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.wswg.config.SecurityConfig;
import com.ssafy.wswg.config.WebMvcConfig;
import com.ssafy.wswg.controller.AiTripRecommendationController;
import com.ssafy.wswg.model.dto.AiTripCandidateDto;
import com.ssafy.wswg.model.dto.AiTripCandidateRequest;
import com.ssafy.wswg.model.dto.AiTripCandidateResponse;
import com.ssafy.wswg.model.dto.AiTripRecommendationDto;
import com.ssafy.wswg.model.dto.AiTripRecommendationRequest;
import com.ssafy.wswg.model.dto.AiTripRecommendationResponse;
import com.ssafy.wswg.model.service.AiTripRecommendationService;
import com.ssafy.wswg.security.JwtAuthenticationFilter;

@WebMvcTest(controllers = AiTripRecommendationController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class},
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, WebMvcConfig.class,
                        JwtAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
class AiTripRecommendationControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockBean
    AiTripRecommendationService aiTripRecommendationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createCandidates_returnsSessionAndCandidates() throws Exception {
        AiTripCandidateDto candidate = new AiTripCandidateDto();
        candidate.setCandidateId("c1");
        candidate.setName("전주 한옥마을");

        AiTripCandidateResponse response = new AiTripCandidateResponse();
        response.setSessionId("session-1");
        response.setReply("후보를 골라봤어요.");
        response.setCandidates(List.of(candidate));
        response.setNextQuestion("무엇이 좋으세요?");
        given(aiTripRecommendationService.createCandidates(any())).willReturn(response);

        AiTripCandidateRequest request = new AiTripCandidateRequest();
        request.setMessage("조용한 여행");

        mockMvc.perform(post("/api/ai/trip-candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-1"))
                .andExpect(jsonPath("$.candidates[0].candidateId").value("c1"))
                .andExpect(jsonPath("$.candidates[0].name").value("전주 한옥마을"));
    }

    @Test
    void recommend_returnsRankedRecommendations() throws Exception {
        AiTripRecommendationDto recommendation = new AiTripRecommendationDto();
        recommendation.setContentId(1001);
        recommendation.setTitle("전주 한옥마을");
        recommendation.setScore(0.91);

        AiTripRecommendationResponse response = new AiTripRecommendationResponse();
        response.setSessionId("session-1");
        response.setReply("추천 결과입니다.");
        response.setRecommendations(List.of(recommendation));
        given(aiTripRecommendationService.recommend(any())).willReturn(response);

        AiTripRecommendationRequest request = new AiTripRecommendationRequest();
        request.setSessionId("session-1");
        request.setSelectedCandidateIds(List.of("c1"));

        mockMvc.perform(post("/api/ai/trip-recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[0].contentId").value(1001))
                .andExpect(jsonPath("$.recommendations[0].title").value("전주 한옥마을"))
                .andExpect(jsonPath("$.recommendations[0].score").value(0.91));
    }
}
