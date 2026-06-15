package com.ssafy.wswg.model.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;
import com.ssafy.wswg.external.openai.OpenAiEmbeddingClient;
import com.ssafy.wswg.model.dao.AttractionDao;
import com.ssafy.wswg.model.dto.NearbyAttractionDto;
import com.ssafy.wswg.model.dto.NearbyAttractionRecommendRequestDto;
import com.ssafy.wswg.model.dto.SemanticAttractionDto;
import com.ssafy.wswg.model.dto.SemanticAttractionRecommendRequestDto;

@Service
@Transactional(readOnly = true)
public class AttractionService {
    private static final int DEFAULT_RADIUS_METERS = 3000;
    private static final int MAX_RADIUS_METERS = 50000;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final String EMBEDDING_MODEL = "text-embedding-3-small";

    private final AttractionDao attractionDao;
    private final OpenAiEmbeddingClient openAiEmbeddingClient;

    public AttractionService(AttractionDao attractionDao, OpenAiEmbeddingClient openAiEmbeddingClient) {
        this.attractionDao = attractionDao;
        this.openAiEmbeddingClient = openAiEmbeddingClient;
    }

    public List<NearbyAttractionDto> recommendNearbyAttractions(NearbyAttractionRecommendRequestDto request) {
        validateCoordinate(request.getLatitude(), request.getLongitude());

        int normalizedRadiusMeters = normalizeRadiusMeters(request.getRadiusMeters());
        int normalizedLimit = normalizeLimit(request.getLimit());

        return attractionDao.findNearbyAttractions(
                request.getLatitude(),
                request.getLongitude(),
                normalizedRadiusMeters,
                request.getContentTypeId(),
                normalizedLimit);
    }

    public List<SemanticAttractionDto> recommendSemanticNearbyAttractions(
            SemanticAttractionRecommendRequestDto request) {
        validateCoordinate(request.getLatitude(), request.getLongitude());
        validateQuery(request.getQuery());

        int normalizedRadiusMeters = normalizeRadiusMeters(request.getRadiusMeters());
        int normalizedLimit = normalizeLimit(request.getLimit());
        String queryEmbedding = toVectorLiteral(openAiEmbeddingClient.createEmbedding(request.getQuery().trim()));

        return attractionDao.findSemanticNearbyAttractions(
                request.getLatitude(),
                request.getLongitude(),
                normalizedRadiusMeters,
                request.getContentTypeId(),
                queryEmbedding,
                EMBEDDING_MODEL,
                normalizedLimit);
    }

    private void validateCoordinate(Double latitude, Double longitude) {
        if (latitude == null || longitude == null
                || latitude < -90 || latitude > 90
                || longitude < -180 || longitude > 180) {
            throw new CommonException(ErrorCode.INVALID_NEARBY_RECOMMEND_REQUEST);
        }
    }

    private int normalizeRadiusMeters(Integer radiusMeters) {
        if (radiusMeters == null) {
            return DEFAULT_RADIUS_METERS;
        }

        if (radiusMeters <= 0 || radiusMeters > MAX_RADIUS_METERS) {
            throw new CommonException(ErrorCode.INVALID_NEARBY_RECOMMEND_REQUEST);
        }

        return radiusMeters;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }

        if (limit <= 0 || limit > MAX_LIMIT) {
            throw new CommonException(ErrorCode.INVALID_NEARBY_RECOMMEND_REQUEST);
        }

        return limit;
    }

    private void validateQuery(String query) {
        if (query == null || query.trim().isEmpty() || query.trim().length() > 500) {
            throw new CommonException(ErrorCode.INVALID_SEMANTIC_RECOMMEND_REQUEST);
        }
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
}
