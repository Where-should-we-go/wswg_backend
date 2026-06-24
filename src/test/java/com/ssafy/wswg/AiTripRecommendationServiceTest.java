package com.ssafy.wswg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;
import com.ssafy.wswg.external.openai.OpenAiEmbeddingClient;
import com.ssafy.wswg.external.openai.OpenAiTripCandidateClient;
import com.ssafy.wswg.model.dao.AttractionDao;
import com.ssafy.wswg.model.dto.AiTripCandidateDto;
import com.ssafy.wswg.model.dto.AiTripCandidateRequest;
import com.ssafy.wswg.model.dto.AiTripCandidateResponse;
import com.ssafy.wswg.model.dto.AiTripRecommendationMatchDto;
import com.ssafy.wswg.model.dto.AiTripRecommendationRequest;
import com.ssafy.wswg.model.dto.AiTripRecommendationResponse;
import com.ssafy.wswg.model.service.AiTripRecommendationService;

@ExtendWith(MockitoExtension.class)
class AiTripRecommendationServiceTest {
    @Mock
    AttractionDao attractionDao;

    @Mock
    OpenAiTripCandidateClient tripCandidateClient;

    @Mock
    OpenAiEmbeddingClient embeddingClient;

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    ValueOperations<String, String> valueOperations;

    private final Map<String, String> redisValues = new HashMap<>();
    private AiTripRecommendationService service;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        org.mockito.Mockito.lenient()
                .when(valueOperations.get(anyString()))
                .thenAnswer(invocation -> redisValues.get(invocation.getArgument(0)));
        org.mockito.Mockito.doAnswer(invocation -> {
            redisValues.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        service = new AiTripRecommendationService(attractionDao, tripCandidateClient, embeddingClient, redisTemplate);
    }

    @Test
    void createCandidates_storesSessionWithGeneratedCandidateIds() {
        AiTripCandidateRequest request = new AiTripCandidateRequest();
        request.setMessage("부모님과 조용한 여행");
        request.setCount(2);

        AiTripCandidateResponse aiResponse = new AiTripCandidateResponse();
        aiResponse.setReply("후보를 골라봤어요.");
        aiResponse.setCandidates(List.of(candidate("전주 한옥마을", "전북 전주"), candidate("경주 보문단지", "경북 경주")));
        aiResponse.setNextQuestion("어떤 후보가 좋으세요?");
        given(tripCandidateClient.createCandidates("부모님과 조용한 여행", 2)).willReturn(aiResponse);

        AiTripCandidateResponse response = service.createCandidates(request);

        assertThat(response.getSessionId()).isNotBlank();
        assertThat(response.getCandidates()).extracting(AiTripCandidateDto::getCandidateId)
                .containsExactly("c1", "c2");
        assertThat(redisValues).containsKey("ai:trip-candidate:" + response.getSessionId());
    }

    @Test
    void recommend_embedsSelectedCandidatesAndRanksMatches() {
        AiTripCandidateRequest candidateRequest = new AiTripCandidateRequest();
        candidateRequest.setMessage("조용한 역사 여행");
        candidateRequest.setCount(2);

        AiTripCandidateResponse aiResponse = new AiTripCandidateResponse();
        aiResponse.setCandidates(List.of(candidate("전주 한옥마을", "전북 전주"), candidate("경주 보문단지", "경북 경주")));
        given(tripCandidateClient.createCandidates(anyString(), eq(2))).willReturn(aiResponse);
        String sessionId = service.createCandidates(candidateRequest).getSessionId();

        given(embeddingClient.createEmbedding(anyString())).willReturn(List.of(0.1, 0.2, 0.3));
        given(embeddingClient.getEmbeddingModel()).willReturn("text-embedding-3-small");
        given(attractionDao.findAiTripRecommendationMatches(
                any(), any(), any(), any(), anyString(), eq("text-embedding-3-small"), anyInt()))
                .willReturn(List.of(match(1001, "전주 한옥마을", 0.91, 1000.0),
                        match(1002, "전주 경기전", 0.86, 1200.0)));

        AiTripRecommendationRequest request = new AiTripRecommendationRequest();
        request.setSessionId(sessionId);
        request.setSelectedCandidateIds(List.of("c1"));
        request.setLatitude(37.5665);
        request.setLongitude(126.9780);
        request.setRadiusMeters(50000);
        request.setLimit(2);

        AiTripRecommendationResponse response = service.recommend(request);

        assertThat(response.getRecommendations()).hasSize(2);
        assertThat(response.getRecommendations().get(0).getContentId()).isEqualTo(1001);
        assertThat(response.getRecommendations().get(0).getMatchedCandidateId()).isEqualTo("c1");
        assertThat(response.getRecommendations().get(0).getScore()).isGreaterThan(0.0);

        ArgumentCaptor<String> embeddingTextCaptor = ArgumentCaptor.forClass(String.class);
        verify(embeddingClient).createEmbedding(embeddingTextCaptor.capture());
        assertThat(embeddingTextCaptor.getValue()).contains("전주 한옥마을", "전북 전주");
    }

    @Test
    void recommend_unknownCandidate_throwsBadRequest() {
        AiTripCandidateRequest candidateRequest = new AiTripCandidateRequest();
        candidateRequest.setMessage("바다 여행");
        candidateRequest.setCount(1);

        AiTripCandidateResponse aiResponse = new AiTripCandidateResponse();
        aiResponse.setCandidates(List.of(candidate("부산 송정해수욕장", "부산")));
        given(tripCandidateClient.createCandidates(anyString(), eq(1))).willReturn(aiResponse);
        String sessionId = service.createCandidates(candidateRequest).getSessionId();

        AiTripRecommendationRequest request = new AiTripRecommendationRequest();
        request.setSessionId(sessionId);
        request.setSelectedCandidateIds(List.of("missing"));

        assertThatThrownBy(() -> service.recommend(request))
                .isInstanceOf(CommonException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_AI_RECOMMENDATION_REQUEST);
    }

    private AiTripCandidateDto candidate(String name, String regionHint) {
        AiTripCandidateDto candidate = new AiTripCandidateDto();
        candidate.setName(name);
        candidate.setRegionHint(regionHint);
        candidate.setDescription(name + " 설명");
        candidate.setReason("사용자 요청과 어울림");
        return candidate;
    }

    private AiTripRecommendationMatchDto match(
            Integer contentId,
            String title,
            Double similarity,
            Double distanceMeters) {
        AiTripRecommendationMatchDto match = new AiTripRecommendationMatchDto();
        match.setNo(contentId.longValue());
        match.setContentId(contentId);
        match.setTitle(title);
        match.setSidoName("전북");
        match.setGugunName("전주");
        match.setAddr1("전북 전주시");
        match.setSimilarity(similarity);
        match.setDistanceMeters(distanceMeters);
        return match;
    }
}
