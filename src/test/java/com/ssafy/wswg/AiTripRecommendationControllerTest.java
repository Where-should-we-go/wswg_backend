package com.ssafy.wswg;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.wswg.config.SecurityConfig;
import com.ssafy.wswg.config.WebMvcConfig;
import com.ssafy.wswg.controller.AiTripRecommendationController;
import com.ssafy.wswg.model.dto.AiTripCandidateDto;
import com.ssafy.wswg.model.dto.AiTripCandidateRequest;
import com.ssafy.wswg.model.dto.AiTripCandidateResponse;
import com.ssafy.wswg.model.dto.AiTripPlanCreateRequest;
import com.ssafy.wswg.model.dto.AiTripRecommendationDto;
import com.ssafy.wswg.model.dto.AiTripRecommendationRequest;
import com.ssafy.wswg.model.dto.AiTripRecommendationResponse;
import com.ssafy.wswg.model.dto.TripDto;
import com.ssafy.wswg.model.service.AiTripPlanService;
import com.ssafy.wswg.model.service.AiTripRecommendationService;
import com.ssafy.wswg.security.JwtAuthenticationFilter;
import com.ssafy.wswg.security.LoginUserId;

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
@Import(AiTripRecommendationControllerTest.TestLoginUserIdConfig.class)
class AiTripRecommendationControllerTest {
    private static final Long LOGIN_USER_ID = 1L;

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AiTripRecommendationService aiTripRecommendationService;

    @MockBean
    AiTripPlanService aiTripPlanService;

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

    @Test
    void createPlan_returnsCreatedTrip() throws Exception {
        TripDto trip = new TripDto();
        trip.setTripId(10L);
        trip.setTitle("AI 전주 여행");
        trip.setUserId(LOGIN_USER_ID);
        given(aiTripPlanService.createPlan(any(), any())).willReturn(trip);

        AiTripPlanCreateRequest request = new AiTripPlanCreateRequest();
        request.setTitle("AI 전주 여행");
        request.setSessionId("session-1");
        request.setSelectedCandidateIds(List.of("c1"));

        mockMvc.perform(post("/api/ai/trip-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.LOCATION, "/api/trips/10"))
                .andExpect(jsonPath("$.tripId").value(10))
                .andExpect(jsonPath("$.title").value("AI 전주 여행"));
    }

    @TestConfiguration
    static class TestLoginUserIdConfig implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new HandlerMethodArgumentResolver() {
                @Override
                public boolean supportsParameter(MethodParameter parameter) {
                    return parameter.hasParameterAnnotation(LoginUserId.class);
                }

                @Override
                public Object resolveArgument(
                        MethodParameter parameter,
                        ModelAndViewContainer mavContainer,
                        NativeWebRequest webRequest,
                        WebDataBinderFactory binderFactory) {
                    return LOGIN_USER_ID;
                }
            });
        }
    }
}
