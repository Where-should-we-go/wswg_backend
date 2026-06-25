package com.ssafy.wswg.model.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;
import com.ssafy.wswg.model.dao.GroupDao;
import com.ssafy.wswg.model.dao.TripDao;
import com.ssafy.wswg.model.dto.MyPageTripResponse;
import com.ssafy.wswg.model.dto.TripCreateRequestDto;
import com.ssafy.wswg.model.dto.TripDto;
import com.ssafy.wswg.model.dto.TripStatus;
import com.ssafy.wswg.model.dto.TripUpdateRequestDto;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TripService {
    private static final String SCOPE_MINE = "mine";
    private static final String SCOPE_JOINED = "joined";
    private static final int MAX_TITLE_LENGTH = 255;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TripDao tripDao;
    private final GroupDao groupDao;

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

    @Transactional
    public TripDto createTrip(Long userId, TripCreateRequestDto request) {
        TripDto trip = new TripDto();
        trip.setTitle(normalizeTitle(request == null ? null : request.getTitle()));
        trip.setStartDate(request == null ? null : request.getStartDate());
        trip.setEndDate(request == null ? null : request.getEndDate());
        validateDates(trip.getStartDate(), trip.getEndDate());

        Long groupId = request == null ? null : request.getGroupId();
        if (groupId == null) {
            trip.setUserId(userId);
        } else {
            validateGroupOwner(groupId, userId);
            trip.setGroupId(groupId);
        }

        trip.setData(normalizeData(request == null ? null : request.getData()));
        tripDao.createTrip(trip);

        return readTrip(trip.getTripId(), userId);
    }

    public TripDto readTrip(Long tripId, Long userId) {
        TripDto trip = findTrip(tripId);
        validateReadable(trip, userId);

        return trip;
    }

    public List<TripDto> readMyTrips(Long userId) {
        return tripDao.readTripsByUserId(userId);
    }

    public List<TripDto> readGroupTrips(Long groupId, Long userId) {
        validateGroupMember(groupId, userId);

        return tripDao.readTripsByGroupId(groupId);
    }

    @Transactional
    public TripDto updateTrip(Long tripId, Long userId, TripUpdateRequestDto request) {
        TripDto existingTrip = findTrip(tripId);
        validateEditable(existingTrip, userId);

        TripDto trip = new TripDto();
        trip.setTripId(tripId);
        trip.setTitle(normalizeTitle(request == null ? null : request.getTitle()));
        trip.setStartDate(request == null ? null : request.getStartDate());
        trip.setEndDate(request == null ? null : request.getEndDate());
        validateDates(trip.getStartDate(), trip.getEndDate());
        trip.setData(normalizeData(request == null ? null : request.getData()));

        if (tripDao.updateTrip(trip) == 0) {
            throw new CommonException(ErrorCode.NOT_FOUND_TRIP);
        }

        return readTrip(tripId, userId);
    }

    // 제목·기간 컬럼만 갱신(data JSONB 미접근). 공동편집 중 제목/날짜 저장이 Redis state 와
    // 무관하게 동작하도록 — 전체 PUT 이 data 를 덮어써 flush 워커와 충돌하는 문제를 피한다.
    @Transactional
    public TripDto updateTripMeta(Long tripId, Long userId, TripUpdateRequestDto request) {
        TripDto existingTrip = findTrip(tripId);
        // 제목·날짜는 여행 내용 편집이므로 본문 편집(updateTrip)과 동일하게 그룹 멤버 허용.
        validateEditable(existingTrip, userId);

        String title = normalizeTitle(request == null ? null : request.getTitle());
        LocalDate startDate = request == null ? null : request.getStartDate();
        LocalDate endDate = request == null ? null : request.getEndDate();
        validateDates(startDate, endDate);

        if (tripDao.updateTripMeta(tripId, title, startDate, endDate) == 0) {
            throw new CommonException(ErrorCode.NOT_FOUND_TRIP);
        }

        return readTrip(tripId, userId);
    }

    @Transactional
    public void deleteTrip(Long tripId, Long userId) {
        TripDto trip = findTrip(tripId);
        validateWritable(trip, userId);

        if (tripDao.deleteTrip(tripId) == 0) {
            throw new CommonException(ErrorCode.NOT_FOUND_TRIP);
        }
    }

    private TripDto findTrip(Long tripId) {
        if (tripId == null) {
            throw new CommonException(ErrorCode.NOT_FOUND_TRIP);
        }

        TripDto trip = tripDao.readTripById(tripId);
        if (trip == null) {
            throw new CommonException(ErrorCode.NOT_FOUND_TRIP);
        }

        return trip;
    }

    private void validateReadable(TripDto trip, Long userId) {
        if (trip.getUserId() != null && trip.getUserId().equals(userId)) {
            return;
        }

        if (trip.getGroupId() != null && groupDao.countGroupMember(trip.getGroupId(), userId) > 0) {
            return;
        }

        throw new CommonException(ErrorCode.TRIP_ACCESS_DENIED);
    }

    private void validateWritable(TripDto trip, Long userId) {
        if (trip.getUserId() != null && trip.getUserId().equals(userId)) {
            return;
        }

        if (trip.getGroupId() != null && groupDao.countGroupOwner(trip.getGroupId(), userId) > 0) {
            return;
        }

        throw new CommonException(ErrorCode.TRIP_ACCESS_DENIED);
    }

    // 날짜·동행 등 여행 내용 편집은 그룹 멤버 누구나 가능(삭제는 validateWritable 로 소유자만).
    private void validateEditable(TripDto trip, Long userId) {
        if (trip.getUserId() != null && trip.getUserId().equals(userId)) {
            return;
        }

        if (trip.getGroupId() != null && groupDao.countGroupMember(trip.getGroupId(), userId) > 0) {
            return;
        }

        throw new CommonException(ErrorCode.TRIP_ACCESS_DENIED);
    }

    private void validateGroupOwner(Long groupId, Long userId) {
        if (groupId == null || groupDao.countGroupById(groupId) == 0) {
            throw new CommonException(ErrorCode.NOT_FOUND_GROUP);
        }

        if (groupDao.countGroupOwner(groupId, userId) == 0) {
            throw new CommonException(ErrorCode.GROUP_OWNER_REQUIRED);
        }
    }

    private void validateGroupMember(Long groupId, Long userId) {
        if (groupId == null || groupDao.countGroupById(groupId) == 0) {
            throw new CommonException(ErrorCode.NOT_FOUND_GROUP);
        }

        if (groupDao.countGroupMember(groupId, userId) == 0) {
            throw new CommonException(ErrorCode.GROUP_MEMBER_REQUIRED);
        }
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            throw new CommonException(ErrorCode.INVALID_TRIP_REQUEST);
        }

        String trimmed = title.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_TITLE_LENGTH) {
            throw new CommonException(ErrorCode.INVALID_TRIP_REQUEST);
        }

        return trimmed;
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new CommonException(ErrorCode.INVALID_TRIP_REQUEST);
        }
    }

    private JsonNode normalizeData(JsonNode data) {
        if (data == null || data.isNull()) {
            ObjectNode defaultData = OBJECT_MAPPER.createObjectNode();
            defaultData.putArray("items");
            return defaultData;
        }

        if (!data.isObject()) {
            throw new CommonException(ErrorCode.INVALID_TRIP_REQUEST);
        }

        return data;
    }
}
