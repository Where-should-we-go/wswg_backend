package com.ssafy.wswg;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.wswg.model.dao.TripDao;
import com.ssafy.wswg.model.dto.MyPageTripResponse;
import com.ssafy.wswg.model.dto.TripDto;
import com.ssafy.wswg.model.dto.TripStatus;

class TripDaoPgTest extends AbstractPostgisIntegrationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TripDao tripDao;

    @Test
    @DisplayName("마이페이지 내 여행 목록은 오늘 날짜 기준으로 예정/진행중/완료 상태를 산출한다")
    void readMyTrips_returnsTripsWithStatus() {
        Long userId = insertUser("trip-owner@example.com");
        insertPersonalTrip("지난 개인 여행", userId, "-5 days", "-1 day");
        insertPersonalTrip("진행중 개인 여행", userId, "-1 day", "1 day");
        insertPersonalTrip("예정 개인 여행", userId, "1 day", "3 days");

        List<MyPageTripResponse> trips = tripDao.readMyTrips(userId, null);

        Map<String, MyPageTripResponse> byTitle = trips.stream()
                .collect(Collectors.toMap(MyPageTripResponse::getTitle, Function.identity()));
        assertThat(byTitle.get("지난 개인 여행").getStatus()).isEqualTo(TripStatus.COMPLETED);
        assertThat(byTitle.get("진행중 개인 여행").getStatus()).isEqualTo(TripStatus.ONGOING);
        assertThat(byTitle.get("예정 개인 여행").getStatus()).isEqualTo(TripStatus.UPCOMING);
        assertThat(byTitle.get("예정 개인 여행").getStatusLabel()).isEqualTo("예정");
    }

    @Test
    @DisplayName("참여 여행 목록은 사용자가 속한 그룹의 여행만 조회한다")
    void readJoinedTrips_returnsGroupTripsForJoinedGroupsOnly() {
        Long userId = insertUser("joined-user@example.com");
        Long otherUserId = insertUser("other-user@example.com");
        Long joinedGroupId = insertGroup("함께 가는 모임", userId);
        Long otherGroupId = insertGroup("남의 모임", otherUserId);
        insertGroupMember(joinedGroupId, userId);
        insertGroupMember(otherGroupId, otherUserId);

        insertGroupTrip("참여 그룹 여행", joinedGroupId, "0 days", "2 days");
        insertGroupTrip("다른 그룹 여행", otherGroupId, "0 days", "2 days");

        List<MyPageTripResponse> trips = tripDao.readJoinedTrips(userId, null);

        assertThat(trips).extracting(MyPageTripResponse::getTitle)
                .containsExactly("참여 그룹 여행");
        assertThat(trips.get(0).getGroupName()).isEqualTo("함께 가는 모임");
        assertThat(trips.get(0).getStatus()).isEqualTo(TripStatus.ONGOING);
    }

    @Test
    @DisplayName("마이페이지 여행 목록은 상태 기준으로 필터링할 수 있다")
    void readMyTrips_filtersByStatus() {
        Long userId = insertUser("trip-filter@example.com");
        insertPersonalTrip("지난 여행", userId, "-5 days", "-1 day");
        insertPersonalTrip("여행중 여행", userId, "-1 day", "1 day");
        insertPersonalTrip("예정 여행", userId, "1 day", "3 days");

        List<MyPageTripResponse> trips = tripDao.readMyTrips(userId, TripStatus.ONGOING.name());

        assertThat(trips).extracting(MyPageTripResponse::getTitle)
                .containsExactly("여행중 여행");
        assertThat(trips.get(0).getStatusLabel()).isEqualTo("여행중");
    }

    @Test
    void createTripAndReadTripById_roundTripsJsonbData() {
        Long userId = insertUser("trip-owner@example.com");

        TripDto trip = new TripDto();
        trip.setTitle("제주 여행");
        trip.setStartDate(LocalDate.of(2026, 7, 1));
        trip.setEndDate(LocalDate.of(2026, 7, 3));
        trip.setUserId(userId);
        ObjectNode data = objectMapper.createObjectNode();
        data.putArray("items")
                .addObject()
                .put("title", "협재해수욕장");
        trip.setData(data);

        tripDao.createTrip(trip);

        TripDto found = tripDao.readTripById(trip.getTripId());

        assertThat(found.getTitle()).isEqualTo("제주 여행");
        assertThat(found.getUserId()).isEqualTo(userId);
        assertThat(found.getData().get("items").get(0).get("title").asText()).isEqualTo("협재해수욕장");
    }

    @Test
    void readTripsByGroupId_returnsOnlyGroupTrips() {
        Long ownerId = insertUser("trip-group-owner@example.com");
        Long groupId = insertGroup("여행 모임", ownerId);
        Long otherGroupId = insertGroup("다른 모임", ownerId);
        insertGroupTrip("모임 여행", groupId);
        insertGroupTrip("다른 모임 여행", otherGroupId);

        List<TripDto> trips = tripDao.readTripsByGroupId(groupId);

        assertThat(trips).extracting(TripDto::getTitle)
                .containsExactly("모임 여행");
        assertThat(trips.get(0).getGroupName()).isEqualTo("여행 모임");
    }

    @Test
    void updateTripAndDeleteTrip_modifyPersistedTrip() {
        Long userId = insertUser("trip-update-owner@example.com");
        TripDto trip = insertPersonalTrip("수정 전", userId);

        trip.setTitle("수정 후");
        trip.setStartDate(LocalDate.of(2026, 8, 1));
        trip.setEndDate(LocalDate.of(2026, 8, 2));
        trip.setData(objectMapper.createObjectNode().put("memo", "updated"));

        assertThat(tripDao.updateTrip(trip)).isEqualTo(1);
        assertThat(tripDao.readTripById(trip.getTripId()).getTitle()).isEqualTo("수정 후");
        assertThat(tripDao.readTripById(trip.getTripId()).getData().get("memo").asText()).isEqualTo("updated");

        assertThat(tripDao.deleteTrip(trip.getTripId())).isEqualTo(1);
        assertThat(tripDao.readTripById(trip.getTripId())).isNull();
    }

    private Long insertUser(String email) {
        return jdbc.queryForObject(
                "INSERT INTO users (email, name) VALUES (?, ?) RETURNING id",
                Long.class,
                email,
                email);
    }

    private Long insertGroup(String groupName, Long ownerId) {
        return jdbc.queryForObject(
                "INSERT INTO groups (group_name, owner_id) VALUES (?, ?) RETURNING group_id",
                Long.class,
                groupName,
                ownerId);
    }

    private void insertGroupMember(Long groupId, Long userId) {
        jdbc.update(
                "INSERT INTO user_group (group_id, user_id) VALUES (?, ?)",
                groupId,
                userId);
    }

    private void insertPersonalTrip(String title, Long userId, String startOffset, String endOffset) {
        jdbc.update(
                "INSERT INTO trips (title, start_date, end_date, user_id, data) "
                        + "VALUES (?, CURRENT_DATE + (?::interval), CURRENT_DATE + (?::interval), ?, '{}'::jsonb)",
                title,
                startOffset,
                endOffset,
                userId);
    }

    private void insertGroupTrip(String title, Long groupId, String startOffset, String endOffset) {
        jdbc.update(
                "INSERT INTO trips (title, start_date, end_date, group_id, data) "
                        + "VALUES (?, CURRENT_DATE + (?::interval), CURRENT_DATE + (?::interval), ?, '{}'::jsonb)",
                title,
                startOffset,
                endOffset,
                groupId);
    }

    private void insertGroupTrip(String title, Long groupId) {
        jdbc.update(
                "INSERT INTO trips (title, start_date, end_date, group_id, data) "
                        + "VALUES (?, CURRENT_DATE, CURRENT_DATE + INTERVAL '1 day', ?, '{}'::jsonb)",
                title,
                groupId);
    }

    private TripDto insertPersonalTrip(String title, Long userId) {
        TripDto trip = new TripDto();
        trip.setTitle(title);
        trip.setStartDate(LocalDate.of(2026, 7, 1));
        trip.setEndDate(LocalDate.of(2026, 7, 3));
        trip.setUserId(userId);
        trip.setData(objectMapper.createObjectNode());
        tripDao.createTrip(trip);
        return trip;
    }
}
