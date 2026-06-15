package com.ssafy.wswg.model.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;
import com.ssafy.wswg.model.dao.AttractionDao;
import com.ssafy.wswg.model.dto.NearbyAttractionDto;

@Service
@Transactional(readOnly = true)
public class AttractionService {
    private static final int DEFAULT_RADIUS_METERS = 3000;
    private static final int MAX_RADIUS_METERS = 50000;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final AttractionDao attractionDao;

    public AttractionService(AttractionDao attractionDao) {
        this.attractionDao = attractionDao;
    }

    public List<NearbyAttractionDto> recommendNearbyAttractions(
            Double latitude,
            Double longitude,
            Integer radiusMeters,
            Integer contentTypeId,
            Integer limit) {
        validateCoordinate(latitude, longitude);

        int normalizedRadiusMeters = normalizeRadiusMeters(radiusMeters);
        int normalizedLimit = normalizeLimit(limit);

        return attractionDao.findNearbyAttractions(
                latitude,
                longitude,
                normalizedRadiusMeters,
                contentTypeId,
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
}
