package com.ssafy.wswg;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ssafy.wswg.model.dao.GroupDao;
import com.ssafy.wswg.model.dto.GroupFootprintDto;
import com.ssafy.wswg.model.dto.GroupMediaDto;
import com.ssafy.wswg.model.dto.GroupMediaRequest;

class GroupDaoPgTest extends AbstractPostgisIntegrationTest {
    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    GroupDao groupDao;

    @Test
    @DisplayName("발자취는 완료된 그룹 여행의 방문 권역만 집계하고 대표 미디어를 함께 반환한다")
    void readFootprints_returnsCompletedTripRegionAggregates() {
        Long ownerId = insertUser("footprint-owner@example.com");
        Long groupId = insertGroup("발자취 모임", ownerId);
        Long otherGroupId = insertGroup("다른 모임", ownerId);
        insertGroupMember(groupId, ownerId);

        insertRegion(11, "서울", 110, "종로구");
        insertRegion(26, "부산", 260, "해운대구");
        insertAttraction(1001, "경복궁", 11, 110);
        insertAttraction(1002, "광화문", 11, 110);
        insertAttraction(2001, "해운대", 26, 260);

        Long completedTripId = insertGroupTrip(
                "완료된 서울 여행",
                groupId,
                "-5 days",
                "-1 day",
                """
                        {"items":[
                          {"content_id":1001},
                          {"contentId":1002},
                          {"content_id":"not-number"}
                        ]}
                        """);
        insertGroupTrip(
                "진행중 부산 여행",
                groupId,
                "-1 day",
                "1 day",
                """
                        {"items":[{"content_id":2001}]}
                        """);
        insertGroupTrip(
                "다른 그룹 서울 여행",
                otherGroupId,
                "-5 days",
                "-1 day",
                """
                        {"items":[{"content_id":1001}]}
                        """);
        insertRepresentativeMedia(groupId, completedTripId, 11, 110);

        List<GroupFootprintDto> footprints = groupDao.readFootprints(groupId);

        assertThat(footprints).hasSize(1);
        GroupFootprintDto footprint = footprints.get(0);
        assertThat(footprint.getSidoCode()).isEqualTo(11);
        assertThat(footprint.getSidoName()).isEqualTo("서울");
        assertThat(footprint.getGugunCode()).isEqualTo(110);
        assertThat(footprint.getGugunName()).isEqualTo("종로구");
        assertThat(footprint.getVisitCount()).isEqualTo(1);
        assertThat(footprint.getAttractionCount()).isEqualTo(2);
        assertThat(footprint.getRepresentativeMediaType()).isEqualTo("PHOTO");
        assertThat(footprint.getRepresentativeMediaUrl()).isEqualTo("https://cdn.example.com/seoul.jpg");
        assertThat(footprint.getLatitude()).isEqualTo(37.5759);
        assertThat(footprint.getLongitude()).isEqualTo(126.9768);
    }

    @Test
    @DisplayName("그룹 미디어는 완료된 여행의 items[].media를 권역/관광지별로 펼쳐 반환한다")
    void readMedia_returnsCompletedTripMediaWithRegionAndAttractionFilters() {
        Long ownerId = insertUser("media-owner@example.com");
        Long groupId = insertGroup("미디어 모임", ownerId);
        Long otherGroupId = insertGroup("다른 미디어 모임", ownerId);
        insertGroupMember(groupId, ownerId);

        insertRegion(11, "서울", 110, "종로구");
        insertRegion(26, "부산", 260, "해운대구");
        insertAttraction(1001, "경복궁", 11, 110);
        insertAttraction(2001, "해운대", 26, 260);

        insertGroupTrip(
                "완료된 서울 여행",
                groupId,
                "-5 days",
                "-1 day",
                """
                        {"items":[
                          {
                            "id":"b-1",
                            "content_id":1001,
                            "title":"경복궁 산책",
                            "visitDate":"2026-06-01",
                            "lat":37.5759,
                            "lng":126.9768,
                            "media":[
                              {"type":"PHOTO","url":"https://cdn.example.com/gyeongbok.jpg","metadata":{"w":4032,"h":3024}},
                              {"type":"AUDIO","url":"https://cdn.example.com/gyeongbok.m4a","metadata":{"durationSec":12}}
                            ]
                          },
                          {
                            "id":"b-2",
                            "content_id":null,
                            "title":"직접 추가 메모",
                            "media":[{"type":"PHOTO","url":"https://cdn.example.com/free.jpg"}]
                          }
                        ]}
                        """);
        insertGroupTrip(
                "진행중 부산 여행",
                groupId,
                "-1 day",
                "1 day",
                """
                        {"items":[{"id":"b-3","content_id":2001,"media":[{"type":"PHOTO","url":"https://cdn.example.com/busan.jpg"}]}]}
                        """);
        insertGroupTrip(
                "다른 그룹 서울 여행",
                otherGroupId,
                "-5 days",
                "-1 day",
                """
                        {"items":[{"id":"b-4","content_id":1001,"media":[{"type":"PHOTO","url":"https://cdn.example.com/other.jpg"}]}]}
                        """);

        List<GroupMediaDto> regionMedia = groupDao.readMedia(groupId, mediaRequest(11, 110, null, null));

        assertThat(regionMedia).hasSize(2);
        assertThat(regionMedia)
                .extracting(GroupMediaDto::getMediaUrl)
                .containsExactly(
                        "https://cdn.example.com/gyeongbok.jpg",
                        "https://cdn.example.com/gyeongbok.m4a");
        GroupMediaDto photo = regionMedia.stream()
                .filter(media -> "PHOTO".equals(media.getMediaType()))
                .findFirst()
                .orElseThrow();
        assertThat(photo.getTripTitle()).isEqualTo("완료된 서울 여행");
        assertThat(photo.getBlockId()).isEqualTo("b-1");
        assertThat(photo.getBlockTitle()).isEqualTo("경복궁 산책");
        assertThat(photo.getContentId()).isEqualTo(1001);
        assertThat(photo.getAttractionTitle()).isEqualTo("경복궁");
        assertThat(photo.getSidoCode()).isEqualTo(11);
        assertThat(photo.getSidoName()).isEqualTo("서울");
        assertThat(photo.getGugunCode()).isEqualTo(110);
        assertThat(photo.getGugunName()).isEqualTo("종로구");
        assertThat(photo.getLatitude()).isEqualTo(37.5759);
        assertThat(photo.getLongitude()).isEqualTo(126.9768);
        assertThat(photo.getMetadata().get("w").asInt()).isEqualTo(4032);

        List<GroupMediaDto> attractionPhotos = groupDao.readMedia(groupId, mediaRequest(null, null, 1001, "PHOTO"));

        assertThat(attractionPhotos).hasSize(1);
        assertThat(attractionPhotos.get(0).getMediaUrl()).isEqualTo("https://cdn.example.com/gyeongbok.jpg");

        List<GroupMediaDto> allMedia = groupDao.readMedia(groupId, mediaRequest(null, null, null, null));

        assertThat(allMedia)
                .extracting(GroupMediaDto::getMediaUrl)
                .containsExactlyInAnyOrder(
                        "https://cdn.example.com/gyeongbok.jpg",
                        "https://cdn.example.com/gyeongbok.m4a",
                        "https://cdn.example.com/free.jpg");
    }

    private Long insertUser(String email) {
        return jdbc.queryForObject(
                "INSERT INTO users (email, name) VALUES (?, ?) RETURNING id",
                Long.class,
                email,
                email);
    }

    private GroupMediaRequest mediaRequest(Integer sidoCode, Integer gugunCode, Integer contentId, String mediaType) {
        GroupMediaRequest request = new GroupMediaRequest();
        request.setSidoCode(sidoCode);
        request.setGugunCode(gugunCode);
        request.setContentId(contentId);
        request.setMediaType(mediaType);

        return request;
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

    private void insertRegion(Integer sidoCode, String sidoName, Integer gugunCode, String gugunName) {
        jdbc.update(
                "INSERT INTO sidos (sido_code, sido_name) VALUES (?, ?) ON CONFLICT DO NOTHING",
                sidoCode,
                sidoName);
        jdbc.update(
                "INSERT INTO guguns (sido_code, gugun_code, gugun_name) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                sidoCode,
                gugunCode,
                gugunName);
    }

    private void insertAttraction(Integer contentId, String title, Integer sidoCode, Integer gugunCode) {
        jdbc.update(
                "INSERT INTO attractions (content_id, title, content_type_id, sido_code, gugun_code, latitude, longitude) "
                        + "VALUES (?, ?, 12, ?, ?, 37.0, 127.0)",
                contentId,
                title,
                sidoCode,
                gugunCode);
    }

    private Long insertGroupTrip(String title, Long groupId, String startOffset, String endOffset, String data) {
        return jdbc.queryForObject(
                "INSERT INTO trips (title, start_date, end_date, group_id, data) "
                        + "VALUES (?, CURRENT_DATE + (?::interval), CURRENT_DATE + (?::interval), ?, ?::jsonb) "
                        + "RETURNING trip_id",
                Long.class,
                title,
                startOffset,
                endOffset,
                groupId,
                data);
    }

    private void insertRepresentativeMedia(Long groupId, Long tripId, Integer sidoCode, Integer gugunCode) {
        jdbc.update(
                "INSERT INTO group_region_media "
                        + "(group_id, trip_id, sido_code, gugun_code, media_type, media_url, geom, metadata) "
                        + "VALUES (?, ?, ?, ?, 'PHOTO', 'https://cdn.example.com/seoul.jpg', "
                        + "ST_SetSRID(ST_MakePoint(126.9768, 37.5759), 4326), '{}'::jsonb)",
                groupId,
                tripId,
                sidoCode,
                gugunCode);
    }
}
