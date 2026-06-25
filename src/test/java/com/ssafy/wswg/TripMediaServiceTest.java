package com.ssafy.wswg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;
import com.ssafy.wswg.model.dao.GroupDao;
import com.ssafy.wswg.model.dao.TripDao;
import com.ssafy.wswg.model.dto.GroupDto;
import com.ssafy.wswg.model.dto.GroupFootprintDto;
import com.ssafy.wswg.model.dto.GroupJoinRequestStatusDto;
import com.ssafy.wswg.model.dto.GroupMediaDto;
import com.ssafy.wswg.model.dto.GroupMediaRequest;
import com.ssafy.wswg.model.dto.GroupMemberDto;
import com.ssafy.wswg.model.dto.MyPageTripResponse;
import com.ssafy.wswg.model.dto.TripDto;
import com.ssafy.wswg.model.dto.TripMediaUploadResponse;
import com.ssafy.wswg.model.service.TripMediaService;
import com.ssafy.wswg.realtime.PlanStateService;
import com.ssafy.wswg.storage.ObjectStorageUploadResult;
import com.ssafy.wswg.storage.ObjectStorageUploader;

class TripMediaServiceTest {
    private static final Long USER_ID = 1L;
    private static final Long TRIP_ID = 10L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RecordingTripDao tripDao;
    private RecordingGroupDao groupDao;
    private RecordingObjectStorageUploader objectStorageUploader;
    private TripMediaService tripMediaService;

    @BeforeEach
    void setUp() throws Exception {
        tripDao = new RecordingTripDao();
        groupDao = new RecordingGroupDao();
        objectStorageUploader = new RecordingObjectStorageUploader();
        tripMediaService = new TripMediaService(tripDao, groupDao, objectStorageUploader, new FakePlanStateService());

        TripDto trip = new TripDto();
        trip.setTripId(TRIP_ID);
        trip.setUserId(USER_ID);
        trip.setData(objectMapper.readTree("""
                {"items":[{"id":"block-1","title":"경복궁"}]}
                """));
        tripDao.trip = trip;
    }

    @Test
    void uploadMedia_storesFileAndAppendsMediaToTripData() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                "fake-image".getBytes());

        TripMediaUploadResponse response = tripMediaService.uploadMedia(TRIP_ID, USER_ID, "block-1", "photo", file);

        assertThat(response.getTripId()).isEqualTo(TRIP_ID);
        assertThat(response.getItemId()).isEqualTo("block-1");
        assertThat(response.getMediaType()).isEqualTo("PHOTO");
        assertThat(response.getMediaUrl()).isEqualTo(objectStorageUploader.url);
        assertThat(objectStorageUploader.objectKey).startsWith("trips/10/items/block-1/");
        assertThat(objectStorageUploader.objectKey).endsWith(".jpg");
        assertThat(objectStorageUploader.contentType).isEqualTo("image/jpeg");

        JsonNode media = tripDao.updatedData.get("items").get(0).get("media").get(0);
        assertThat(media.get("type").asText()).isEqualTo("PHOTO");
        assertThat(media.get("url").asText()).isEqualTo(objectStorageUploader.url);
        assertThat(media.get("metadata").get("originalFilename").asText()).isEqualTo("photo.jpg");
        assertThat(media.get("metadata").get("contentType").asText()).isEqualTo("image/jpeg");
        assertThat(media.get("metadata").get("size").asLong()).isEqualTo(file.getSize());
    }

    @Test
    void uploadMedia_rejectsMismatchedContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "audio.mp3",
                "audio/mpeg",
                "fake-audio".getBytes());

        assertThatThrownBy(() -> tripMediaService.uploadMedia(TRIP_ID, USER_ID, "block-1", "PHOTO", file))
                .isInstanceOf(CommonException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_MEDIA_UPLOAD_REQUEST);
    }

    private static class RecordingObjectStorageUploader implements ObjectStorageUploader {
        private String objectKey;
        private String contentType;
        private final String url = "https://cdn.example.com/photo.jpg";

        @Override
        public ObjectStorageUploadResult upload(
                String objectKey,
                InputStream inputStream,
                long contentLength,
                String contentType) throws IOException {
            this.objectKey = objectKey;
            this.contentType = contentType;

            return new ObjectStorageUploadResult(objectKey, url);
        }
    }

    private static class FakePlanStateService extends PlanStateService {
        FakePlanStateService() {
            super(null, null, null);
        }

        @Override
        public boolean hasState(Long tripId) {
            return false;
        }
    }

    private static class RecordingTripDao implements TripDao {
        private TripDto trip;
        private JsonNode updatedData;

        @Override
        public List<MyPageTripResponse> readMyTrips(Long userId, String status) {
            return List.of();
        }

        @Override
        public List<MyPageTripResponse> readJoinedTrips(Long userId, String status) {
            return List.of();
        }

        @Override
        public int createTrip(TripDto trip) {
            return 0;
        }

        @Override
        public TripDto readTripById(Long tripId) {
            return trip;
        }

        @Override
        public List<TripDto> readTripsByUserId(Long userId) {
            return List.of();
        }

        @Override
        public List<TripDto> readTripsByGroupId(Long groupId) {
            return List.of();
        }

        @Override
        public int updateTrip(TripDto trip) {
            return 0;
        }

        @Override
        public int updateTripData(Long tripId, JsonNode data) {
            updatedData = data;
            return 1;
        }

        @Override
        public int updateTripMeta(Long tripId, String title, java.time.LocalDate startDate, java.time.LocalDate endDate) {
            return 1;
        }

        @Override
        public int deleteTrip(Long tripId) {
            return 0;
        }
    }

    private static class RecordingGroupDao implements GroupDao {
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
            return 0;
        }

        @Override
        public int countGroupMember(Long groupId, Long userId) {
            return 0;
        }

        @Override
        public int countGroupOwner(Long groupId, Long userId) {
            return 0;
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
