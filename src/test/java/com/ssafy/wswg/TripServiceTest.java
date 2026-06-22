package com.ssafy.wswg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;
import com.ssafy.wswg.model.dao.TripDao;
import com.ssafy.wswg.model.dto.MyPageTripResponse;
import com.ssafy.wswg.model.dto.TripStatus;
import com.ssafy.wswg.model.service.TripService;

class TripServiceTest {
    private static final Long USER_ID = 1L;

    private RecordingTripDao tripDao;
    private TripService tripService;

    @BeforeEach
    void setUp() {
        tripDao = new RecordingTripDao();
        tripService = new TripService(tripDao);
    }

    @Test
    void readMyPageTrips_defaultScopeReadsMyTrips() {
        tripService.readMyPageTrips(USER_ID, null, null);

        assertThat(tripDao.myTripsUserId).isEqualTo(USER_ID);
        assertThat(tripDao.myTripsStatus).isNull();
    }

    @Test
    void readMyPageTrips_joinedScopeReadsJoinedTripsWithNormalizedStatus() {
        tripService.readMyPageTrips(USER_ID, "joined", "ongoing");

        assertThat(tripDao.joinedTripsUserId).isEqualTo(USER_ID);
        assertThat(tripDao.joinedTripsStatus).isEqualTo(TripStatus.ONGOING.name());
    }

    @Test
    void readMyPageTrips_invalidScopeThrowsCommonException() {
        assertThatThrownBy(() -> tripService.readMyPageTrips(USER_ID, "all", null))
                .isInstanceOfSatisfying(CommonException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_TRIP_SCOPE));
    }

    @Test
    void readMyPageTrips_invalidStatusThrowsCommonException() {
        assertThatThrownBy(() -> tripService.readMyPageTrips(USER_ID, "mine", "done"))
                .isInstanceOfSatisfying(CommonException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_TRIP_STATUS));
    }

    private static class RecordingTripDao implements TripDao {
        private Long myTripsUserId;
        private String myTripsStatus;
        private Long joinedTripsUserId;
        private String joinedTripsStatus;

        @Override
        public List<MyPageTripResponse> readMyTrips(Long userId, String status) {
            myTripsUserId = userId;
            myTripsStatus = status;
            return List.of();
        }

        @Override
        public List<MyPageTripResponse> readJoinedTrips(Long userId, String status) {
            joinedTripsUserId = userId;
            joinedTripsStatus = status;
            return List.of();
        }
    }
}
