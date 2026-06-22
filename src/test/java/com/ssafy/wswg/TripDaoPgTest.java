package com.ssafy.wswg;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ssafy.wswg.model.dao.TripDao;
import com.ssafy.wswg.model.dto.MyPageTripResponse;
import com.ssafy.wswg.model.dto.TripStatus;

class TripDaoPgTest extends AbstractPostgisIntegrationTest {

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
}
