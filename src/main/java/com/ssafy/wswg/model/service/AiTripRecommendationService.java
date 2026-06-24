package com.ssafy.wswg.model.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;
import com.ssafy.wswg.external.openai.OpenAiEmbeddingClient;
import com.ssafy.wswg.external.openai.OpenAiTripCandidateClient;
import com.ssafy.wswg.model.dao.AttractionDao;
import com.ssafy.wswg.model.dto.AiTripCandidateDto;
import com.ssafy.wswg.model.dto.AiTripCandidateRequest;
import com.ssafy.wswg.model.dto.AiTripCandidateResponse;
import com.ssafy.wswg.model.dto.AiTripRecommendationDto;
import com.ssafy.wswg.model.dto.AiTripRecommendationMatchDto;
import com.ssafy.wswg.model.dto.AiTripRecommendationRequest;
import com.ssafy.wswg.model.dto.AiTripRecommendationResponse;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AiTripRecommendationService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final String SESSION_KEY_FORMAT = "ai:trip-candidate:%s";
    private static final Duration SESSION_TTL = Duration.ofHours(2);
    private static final int DEFAULT_CANDIDATE_COUNT = 8;
    private static final int MAX_CANDIDATE_COUNT = 12;
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 30;
    private static final int DEFAULT_RADIUS_METERS = 50_000;
    private static final int MAX_RADIUS_METERS = 200_000;
    private static final int MATCHES_PER_CANDIDATE_MULTIPLIER = 3;

    private final AttractionDao attractionDao;
    private final OpenAiTripCandidateClient tripCandidateClient;
    private final OpenAiEmbeddingClient embeddingClient;
    private final StringRedisTemplate stringRedisTemplate;

    public AiTripCandidateResponse createCandidates(AiTripCandidateRequest request) {
        String message = normalizeMessage(request == null ? null : request.getMessage());
        int count = normalizeCandidateCount(request == null ? null : request.getCount());

        AiTripCandidateResponse aiResponse = tripCandidateClient.createCandidates(message, count);
        List<AiTripCandidateDto> candidates = normalizeCandidates(aiResponse.getCandidates(), count);

        String sessionId = UUID.randomUUID().toString();
        AiTripCandidateResponse response = new AiTripCandidateResponse();
        response.setSessionId(sessionId);
        response.setReply(defaultText(aiResponse.getReply(), "마음에 드는 후보를 골라주세요."));
        response.setCandidates(candidates);
        response.setNextQuestion(defaultText(aiResponse.getNextQuestion(), "이 중 어떤 후보가 마음에 드세요?"));

        stringRedisTemplate.opsForValue().set(sessionKey(sessionId), writeSession(new CandidateSession(
                message,
                response.getReply(),
                response.getCandidates(),
                response.getNextQuestion(),
                OffsetDateTime.now())), SESSION_TTL);

        return response;
    }

    public AiTripRecommendationResponse recommend(AiTripRecommendationRequest request) {
        validateRecommendationRequest(request);
        CandidateSession session = readSession(request.getSessionId());
        List<AiTripCandidateDto> selectedCandidates = selectedCandidates(session, request.getSelectedCandidateIds());

        int limit = normalizeLimit(request.getLimit());
        Integer radiusMeters = normalizeRadiusMeters(request.getRadiusMeters(), hasCoordinate(request));
        Map<Integer, AiTripRecommendationDto> rankedByContentId = new LinkedHashMap<>();

        for (AiTripCandidateDto candidate : selectedCandidates) {
            String queryText = candidateText(candidate);
            String queryEmbedding = toVectorLiteral(embeddingClient.createEmbedding(queryText));
            List<AiTripRecommendationMatchDto> matches = attractionDao.findAiTripRecommendationMatches(
                    request.getLatitude(),
                    request.getLongitude(),
                    radiusMeters,
                    request.getContentTypeId(),
                    queryEmbedding,
                    embeddingClient.getEmbeddingModel(),
                    Math.min(MAX_LIMIT * MATCHES_PER_CANDIDATE_MULTIPLIER, limit * MATCHES_PER_CANDIDATE_MULTIPLIER));

            for (AiTripRecommendationMatchDto match : matches) {
                AiTripRecommendationDto recommendation = toRecommendation(
                        match,
                        candidate,
                        score(match, candidate, radiusMeters, hasCoordinate(request)));
                rankedByContentId.merge(
                        recommendation.getContentId(),
                        recommendation,
                        (left, right) -> left.getScore() >= right.getScore() ? left : right);
            }
        }

        List<AiTripRecommendationDto> recommendations = rankedByContentId.values().stream()
                .sorted(Comparator.comparing(AiTripRecommendationDto::getScore).reversed()
                        .thenComparing(AiTripRecommendationDto::getContentId))
                .limit(limit)
                .toList();

        AiTripRecommendationResponse response = new AiTripRecommendationResponse();
        response.setSessionId(request.getSessionId());
        response.setReply("선택한 후보와 의미적으로 가까운 실제 관광지를 정리했어요.");
        response.setRecommendations(recommendations);
        response.setNextQuestion("숙소, 음식점, 이동 동선까지 이어서 추천받을까요?");

        return response;
    }

    private String normalizeMessage(String message) {
        if (message == null || message.trim().isEmpty() || message.trim().length() > 1000) {
            throw new CommonException(ErrorCode.INVALID_AI_RECOMMENDATION_REQUEST);
        }

        return message.trim();
    }

    private int normalizeCandidateCount(Integer count) {
        if (count == null) {
            return DEFAULT_CANDIDATE_COUNT;
        }

        if (count <= 0 || count > MAX_CANDIDATE_COUNT) {
            throw new CommonException(ErrorCode.INVALID_AI_RECOMMENDATION_REQUEST);
        }

        return count;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }

        if (limit <= 0 || limit > MAX_LIMIT) {
            throw new CommonException(ErrorCode.INVALID_AI_RECOMMENDATION_REQUEST);
        }

        return limit;
    }

    private Integer normalizeRadiusMeters(Integer radiusMeters, boolean hasCoordinate) {
        if (!hasCoordinate) {
            return null;
        }

        if (radiusMeters == null) {
            return DEFAULT_RADIUS_METERS;
        }

        if (radiusMeters <= 0 || radiusMeters > MAX_RADIUS_METERS) {
            throw new CommonException(ErrorCode.INVALID_AI_RECOMMENDATION_REQUEST);
        }

        return radiusMeters;
    }

    private List<AiTripCandidateDto> normalizeCandidates(List<AiTripCandidateDto> candidates, int count) {
        if (candidates == null || candidates.isEmpty()) {
            throw new CommonException(ErrorCode.AI_TRIP_CANDIDATE_FAILED);
        }

        List<AiTripCandidateDto> normalizedCandidates = new ArrayList<>();
        int index = 1;
        for (AiTripCandidateDto candidate : candidates) {
            if (candidate.getName() == null || candidate.getName().isBlank()) {
                continue;
            }

            AiTripCandidateDto normalized = new AiTripCandidateDto();
            normalized.setCandidateId("c" + index);
            normalized.setName(candidate.getName().trim());
            normalized.setRegionHint(trimToNull(candidate.getRegionHint()));
            normalized.setDescription(defaultText(candidate.getDescription(), candidate.getName().trim()));
            normalized.setReason(defaultText(candidate.getReason(), "사용자 요청과 어울리는 후보입니다."));
            normalizedCandidates.add(normalized);
            index++;
            if (normalizedCandidates.size() == count) {
                break;
            }
        }

        if (normalizedCandidates.isEmpty()) {
            throw new CommonException(ErrorCode.AI_TRIP_CANDIDATE_FAILED);
        }

        return normalizedCandidates;
    }

    private void validateRecommendationRequest(AiTripRecommendationRequest request) {
        if (request == null
                || request.getSessionId() == null
                || request.getSessionId().isBlank()
                || request.getSelectedCandidateIds() == null
                || request.getSelectedCandidateIds().isEmpty()) {
            throw new CommonException(ErrorCode.INVALID_AI_RECOMMENDATION_REQUEST);
        }

        if ((request.getLatitude() == null) != (request.getLongitude() == null)) {
            throw new CommonException(ErrorCode.INVALID_AI_RECOMMENDATION_REQUEST);
        }

        if (hasCoordinate(request)
                && (request.getLatitude() < -90 || request.getLatitude() > 90
                || request.getLongitude() < -180 || request.getLongitude() > 180)) {
            throw new CommonException(ErrorCode.INVALID_AI_RECOMMENDATION_REQUEST);
        }
    }

    private boolean hasCoordinate(AiTripRecommendationRequest request) {
        return request.getLatitude() != null && request.getLongitude() != null;
    }

    private CandidateSession readSession(String sessionId) {
        String sessionJson = stringRedisTemplate.opsForValue().get(sessionKey(sessionId));
        if (sessionJson == null) {
            throw new CommonException(ErrorCode.INVALID_AI_RECOMMENDATION_REQUEST);
        }

        try {
            return OBJECT_MAPPER.readValue(sessionJson, CandidateSession.class);
        } catch (JsonProcessingException e) {
            throw new CommonException(ErrorCode.INVALID_AI_RECOMMENDATION_REQUEST);
        }
    }

    private List<AiTripCandidateDto> selectedCandidates(CandidateSession session, List<String> selectedCandidateIds) {
        Map<String, AiTripCandidateDto> candidatesById = new LinkedHashMap<>();
        for (AiTripCandidateDto candidate : session.candidates()) {
            candidatesById.put(candidate.getCandidateId(), candidate);
        }

        List<AiTripCandidateDto> selected = selectedCandidateIds.stream()
                .distinct()
                .map(candidatesById::get)
                .toList();
        if (selected.stream().anyMatch(candidate -> candidate == null) || selected.isEmpty()) {
            throw new CommonException(ErrorCode.INVALID_AI_RECOMMENDATION_REQUEST);
        }

        return selected;
    }

    private AiTripRecommendationDto toRecommendation(
            AiTripRecommendationMatchDto match,
            AiTripCandidateDto candidate,
            double score) {
        AiTripRecommendationDto recommendation = new AiTripRecommendationDto();
        recommendation.setNo(match.getNo());
        recommendation.setContentId(match.getContentId());
        recommendation.setTitle(match.getTitle());
        recommendation.setContentTypeId(match.getContentTypeId());
        recommendation.setSidoCode(match.getSidoCode());
        recommendation.setSidoName(match.getSidoName());
        recommendation.setGugunCode(match.getGugunCode());
        recommendation.setGugunName(match.getGugunName());
        recommendation.setFirstImage1(match.getFirstImage1());
        recommendation.setFirstImage2(match.getFirstImage2());
        recommendation.setLatitude(match.getLatitude());
        recommendation.setLongitude(match.getLongitude());
        recommendation.setAddr1(match.getAddr1());
        recommendation.setAddr2(match.getAddr2());
        recommendation.setDistanceMeters(match.getDistanceMeters());
        recommendation.setSimilarity(match.getSimilarity());
        recommendation.setScore(score);
        recommendation.setMatchedCandidateId(candidate.getCandidateId());
        recommendation.setMatchedCandidateName(candidate.getName());

        return recommendation;
    }

    private double score(
            AiTripRecommendationMatchDto match,
            AiTripCandidateDto candidate,
            Integer radiusMeters,
            boolean hasCoordinate) {
        double similarity = match.getSimilarity() == null ? 0 : clamp(match.getSimilarity());
        double regionMatch = regionMatches(candidate, match) ? 1 : 0;

        if (!hasCoordinate || radiusMeters == null || match.getDistanceMeters() == null) {
            return round(similarity * 0.85 + regionMatch * 0.15);
        }

        double distanceScore = 1 - Math.min(match.getDistanceMeters(), radiusMeters) / radiusMeters;
        return round(similarity * 0.70 + distanceScore * 0.20 + regionMatch * 0.10);
    }

    private boolean regionMatches(AiTripCandidateDto candidate, AiTripRecommendationMatchDto match) {
        String regionHint = candidate.getRegionHint();
        if (regionHint == null || regionHint.isBlank()) {
            return false;
        }

        String haystack = String.join(" ",
                nullToEmpty(match.getSidoName()),
                nullToEmpty(match.getGugunName()),
                nullToEmpty(match.getAddr1()),
                nullToEmpty(match.getAddr2())).toLowerCase(Locale.ROOT);

        for (String token : regionHint.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!token.isBlank() && haystack.contains(token)) {
                return true;
            }
        }

        return false;
    }

    private String candidateText(AiTripCandidateDto candidate) {
        return String.join("\n",
                candidate.getName(),
                nullToEmpty(candidate.getRegionHint()),
                nullToEmpty(candidate.getDescription()),
                nullToEmpty(candidate.getReason()));
    }

    private String toVectorLiteral(List<Double> embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(embedding.get(i));
        }
        return builder.append(']').toString();
    }

    private String writeSession(CandidateSession session) {
        try {
            return OBJECT_MAPPER.writeValueAsString(session);
        } catch (JsonProcessingException e) {
            throw new CommonException(ErrorCode.AI_TRIP_CANDIDATE_FAILED);
        }
    }

    private String sessionKey(String sessionId) {
        return SESSION_KEY_FORMAT.formatted(sessionId);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private record CandidateSession(
            String message,
            String reply,
            List<AiTripCandidateDto> candidates,
            String nextQuestion,
            OffsetDateTime createdAt) {
    }
}
