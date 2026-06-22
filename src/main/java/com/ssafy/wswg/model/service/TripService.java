package com.ssafy.wswg.model.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;
import com.ssafy.wswg.model.dao.TripDao;
import com.ssafy.wswg.model.dto.MyPageTripResponse;
import com.ssafy.wswg.model.dto.TripStatus;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TripService {
    private static final String SCOPE_MINE = "mine";
    private static final String SCOPE_JOINED = "joined";

    private final TripDao tripDao;

    public List<MyPageTripResponse> readMyPageTrips(Long userId, String scope, String status) {
        String normalizedScope = normalizeScope(scope);
        TripStatus normalizedStatus = normalizeStatus(status);
        String statusName = normalizedStatus == null ? null : normalizedStatus.name();

        if (SCOPE_MINE.equals(normalizedScope)) {
            return tripDao.readMyTrips(userId, statusName);
        }

        return tripDao.readJoinedTrips(userId, statusName);
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return SCOPE_MINE;
        }

        String normalizedScope = scope.trim().toLowerCase();
        if (!SCOPE_MINE.equals(normalizedScope) && !SCOPE_JOINED.equals(normalizedScope)) {
            throw new CommonException(ErrorCode.INVALID_TRIP_SCOPE);
        }

        return normalizedScope;
    }

    private TripStatus normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        try {
            return TripStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CommonException(ErrorCode.INVALID_TRIP_STATUS);
        }
    }
}
