package com.ssafy.wswg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;
import com.ssafy.wswg.model.dao.GroupDao;
import com.ssafy.wswg.model.dao.TripDao;
import com.ssafy.wswg.model.dto.GroupDto;
import com.ssafy.wswg.model.dto.GroupFootprintDto;
import com.ssafy.wswg.model.dto.GroupJoinRequestStatusDto;
import com.ssafy.wswg.model.dto.GroupMemberDto;
import com.ssafy.wswg.model.dto.GroupMediaDto;
import com.ssafy.wswg.model.dto.GroupMediaRequest;
import com.ssafy.wswg.model.dto.MyPageTripResponse;
import com.ssafy.wswg.model.dto.TripCreateRequestDto;
import com.ssafy.wswg.model.dto.TripDto;
import com.ssafy.wswg.model.dto.TripStatus;
import com.ssafy.wswg.model.dto.TripUpdateRequestDto;
import com.ssafy.wswg.model.service.TripService;

class TripServiceTest {
    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long GROUP_ID = 10L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RecordingTripDao tripDao;
    private RecordingGroupDao groupDao;
    private TripService tripService;

    @BeforeEach
    void setUp() {
        tripDao = new RecordingTripDao();
        groupDao = new RecordingGroupDao();
        tripService = new TripService(tripDao, groupDao);
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

    @Test
    void createPersonalTrip_setsLoginUserAsOwner() {
        TripCreateRequestDto request = new TripCreateRequestDto();
        request.setTitle(" 제주 여행 ");
        request.setStartDate(LocalDate.of(2026, 7, 1));
        request.setEndDate(LocalDate.of(2026, 7, 3));

        TripDto trip = tripService.createTrip(USER_ID, request);

        assertThat(trip.getTitle()).isEqualTo("제주 여행");
        assertThat(trip.getUserId()).isEqualTo(USER_ID);
        assertThat(trip.getGroupId()).isNull();
        assertThat(trip.getData().get("items")).isNotNull();
    }

    @Test
    void createGroupTrip_requiresGroupOwner() {
        groupDao.groupExists = true;
        groupDao.owner = false;

        TripCreateRequestDto request = new TripCreateRequestDto();
        request.setTitle("모임 여행");
        request.setGroupId(GROUP_ID);

        assertThatThrownBy(() -> tripService.createTrip(USER_ID, request))
                .isInstanceOfSatisfying(CommonException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GROUP_OWNER_REQUIRED));
    }

    @Test
    void readGroupTrip_allowsGroupMember() {
        groupDao.member = true;
        TripDto groupTrip = new TripDto();
        groupTrip.setTripId(1L);
        groupTrip.setTitle("모임 여행");
        groupTrip.setGroupId(GROUP_ID);
        tripDao.saved.put(1L, groupTrip);

        TripDto trip = tripService.readTrip(1L, USER_ID);

        assertThat(trip.getTitle()).isEqualTo("모임 여행");
    }

    @Test
    void updatePersonalTrip_rejectsOtherUser() {
        TripDto personalTrip = new TripDto();
        personalTrip.setTripId(1L);
        personalTrip.setTitle("남의 여행");
        personalTrip.setUserId(OTHER_USER_ID);
        tripDao.saved.put(1L, personalTrip);

        TripUpdateRequestDto request = new TripUpdateRequestDto();
        request.setTitle("수정");

        assertThatThrownBy(() -> tripService.updateTrip(1L, USER_ID, request))
                .isInstanceOfSatisfying(CommonException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TRIP_ACCESS_DENIED));
    }

    @Test
    void createTrip_rejectsInvalidDateRange() {
        TripCreateRequestDto request = new TripCreateRequestDto();
        request.setTitle("잘못된 여행");
        request.setStartDate(LocalDate.of(2026, 7, 3));
        request.setEndDate(LocalDate.of(2026, 7, 1));

        assertThatThrownBy(() -> tripService.createTrip(USER_ID, request))
                .isInstanceOfSatisfying(CommonException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_TRIP_REQUEST));
    }

    private class RecordingTripDao implements TripDao {
        private Long myTripsUserId;
        private String myTripsStatus;
        private Long joinedTripsUserId;
        private String joinedTripsStatus;
        private long sequence = 1L;
        private final Map<Long, TripDto> saved = new HashMap<>();

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

        @Override
        public int createTrip(TripDto trip) {
            trip.setTripId(sequence++);
            saved.put(trip.getTripId(), copy(trip));
            return 1;
        }

        @Override
        public TripDto readTripById(Long tripId) {
            TripDto trip = saved.get(tripId);
            return trip == null ? null : copy(trip);
        }

        @Override
        public List<TripDto> readTripsByUserId(Long userId) {
            return saved.values().stream()
                    .filter(trip -> userId.equals(trip.getUserId()))
                    .map(this::copy)
                    .toList();
        }

        @Override
        public List<TripDto> readTripsByGroupId(Long groupId) {
            return saved.values().stream()
                    .filter(trip -> groupId.equals(trip.getGroupId()))
                    .map(this::copy)
                    .toList();
        }

        @Override
        public int updateTrip(TripDto trip) {
            TripDto existing = saved.get(trip.getTripId());
            if (existing == null) {
                return 0;
            }

            existing.setTitle(trip.getTitle());
            existing.setStartDate(trip.getStartDate());
            existing.setEndDate(trip.getEndDate());
            existing.setData(trip.getData());
            return 1;
        }

        @Override
        public int updateTripData(Long tripId, com.fasterxml.jackson.databind.JsonNode data) {
            TripDto existing = saved.get(tripId);
            if (existing == null) {
                return 0;
            }

            existing.setData(data);
            return 1;
        }

        @Override
        public int deleteTrip(Long tripId) {
            return saved.remove(tripId) == null ? 0 : 1;
        }

        private TripDto copy(TripDto source) {
            TripDto copy = new TripDto();
            copy.setTripId(source.getTripId());
            copy.setTitle(source.getTitle());
            copy.setStartDate(source.getStartDate());
            copy.setEndDate(source.getEndDate());
            copy.setUserId(source.getUserId());
            copy.setGroupId(source.getGroupId());
            copy.setGroupName(source.getGroupName());
            copy.setData(source.getData() == null ? objectMapper.createObjectNode() : source.getData());
            copy.setCreatedAt(source.getCreatedAt());
            copy.setUpdatedAt(source.getUpdatedAt());
            return copy;
        }
    }

    private static class RecordingGroupDao implements GroupDao {
        private boolean groupExists;
        private boolean owner;
        private boolean member;

        @Override
        public int createGroup(GroupDto group) {
            return 0;
        }

        @Override
        public int addMember(Long groupId, Long userId) {
            return 0;
        }

        @Override
        public int removeMember(Long groupId, Long userId) {
            return 0;
        }

        @Override
        public List<GroupDto> readGroupsByUserId(Long userId) {
            return List.of();
        }

        @Override
        public GroupDto readGroupById(Long groupId, Long userId) {
            return null;
        }

        @Override
        public int countGroupById(Long groupId) {
            return groupExists ? 1 : 0;
        }

        @Override
        public int countGroupMember(Long groupId, Long userId) {
            return member ? 1 : 0;
        }

        @Override
        public int countGroupOwner(Long groupId, Long userId) {
            return owner ? 1 : 0;
        }

        @Override
        public int countUserById(Long userId) {
            return 0;
        }

        @Override
        public List<GroupMemberDto> readMembers(Long groupId) {
            return List.of();
        }

        @Override
        public int createJoinRequest(Long groupId, Long userId) {
            return 0;
        }

        @Override
        public GroupJoinRequestStatusDto readJoinRequest(Long groupId, Long requestId) {
            return null;
        }

        @Override
        public GroupJoinRequestStatusDto readJoinRequestByUser(Long groupId, Long userId) {
            return null;
        }

        @Override
        public List<GroupJoinRequestStatusDto> readPendingJoinRequests(Long groupId) {
            return List.of();
        }

        @Override
        public int approveJoinRequest(Long requestId) {
            return 0;
        }

        @Override
        public List<GroupFootprintDto> readFootprints(Long groupId) {
            return List.of();
        }

        @Override
        public List<GroupMediaDto> readMedia(Long groupId, GroupMediaRequest request) {
            return List.of();
        }

        @Override
        public int updateGroupName(Long groupId, String groupName) {
            return 0;
        }

        @Override
        public int deleteGroup(Long groupId) {
            return 0;
        }
    }
}
