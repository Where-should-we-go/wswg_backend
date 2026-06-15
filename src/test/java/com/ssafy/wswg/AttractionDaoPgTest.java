package com.ssafy.wswg;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ssafy.wswg.model.dao.AttractionDao;
import com.ssafy.wswg.model.dto.AttractionDto;

/**
 * AttractionDao(MyBatis) bulkUpsert가 실제 PostgreSQL/PostGIS에서 동작하는지 검증.
 * insert+geom 자동생성 / 멱등성 / overview·homepage 보존 / null 좌표→null geom /
 * modified_time 갱신을 raw JdbcTemplate로 재조회해 확인.
 * 베이스 클래스의 @Transactional로 각 테스트는 롤백된다.
 */
class AttractionDaoPgTest extends AbstractPostgisIntegrationTest {

    @Autowired
    AttractionDao attractionDao;

    @Autowired
    JdbcTemplate jdbc;

    // 모든 attraction이 참조하는 FK 대상. content_type 12는 init 시드.
    static final int SIDO = 11;
    static final int GUGUN = 110;
    static final int CTYPE = 12;

    @BeforeEach
    void setUpFk() {
        // attractions의 (sido_code) + (sido_code, gugun_code) FK 대상 삽입.
        // @Transactional 롤백되므로 init 시드와 충돌 없음. 안전하게 ON CONFLICT 처리.
        jdbc.update("INSERT INTO sidos (sido_code, sido_name) VALUES (?, ?) "
                + "ON CONFLICT (sido_code) DO NOTHING", SIDO, "서울");
        jdbc.update("INSERT INTO guguns (sido_code, gugun_code, gugun_name) VALUES (?, ?, ?) "
                + "ON CONFLICT (sido_code, gugun_code) DO NOTHING", SIDO, GUGUN, "종로구");
    }

    /** 좌표가 있는 기본 attraction DTO 생성. content_id만 바꿔 가며 사용. */
    private AttractionDto attraction(int contentId, String title) {
        AttractionDto a = new AttractionDto();
        a.setContentId(contentId);
        a.setTitle(title);
        a.setContentTypeId(CTYPE);
        a.setSidoCode(SIDO);
        a.setGugunCode(GUGUN);
        a.setLatitude(37.5759);
        a.setLongitude(126.9769);
        return a;
    }

    @Test
    @DisplayName("bulkUpsert: 행 삽입 + geom 자동생성(ST_X=lng, ST_Y=lat)")
    void bulkUpsert_insertAndGeneratedGeom() {
        int cid = 90001;
        attractionDao.bulkUpsert(List.of(attraction(cid, "경복궁")));

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM attractions WHERE content_id = ?", Integer.class, cid);
        assertThat(count).isEqualTo(1);

        // geom은 lat/lng로부터 GENERATED ALWAYS STORED → 자동 채워짐
        Double geomX = jdbc.queryForObject(
                "SELECT ST_X(geom) FROM attractions WHERE content_id = ?", Double.class, cid);
        Double geomY = jdbc.queryForObject(
                "SELECT ST_Y(geom) FROM attractions WHERE content_id = ?", Double.class, cid);
        assertThat(geomX).isEqualTo(126.9769);
        assertThat(geomY).isEqualTo(37.5759);
    }

    @Test
    @DisplayName("bulkUpsert: 멱등성(같은 content_id 재upsert → 행 1개, title 갱신)")
    void bulkUpsert_idempotent() {
        int cid = 90002;
        attractionDao.bulkUpsert(List.of(attraction(cid, "원래제목")));
        attractionDao.bulkUpsert(List.of(attraction(cid, "수정된제목")));

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM attractions WHERE content_id = ?", Integer.class, cid);
        assertThat(count).isEqualTo(1);

        String title = jdbc.queryForObject(
                "SELECT title FROM attractions WHERE content_id = ?", String.class, cid);
        assertThat(title).isEqualTo("수정된제목");
    }

    @Test
    @DisplayName("★ bulkUpsert: 재upsert가 overview/homepage를 보존(A-6 write-through 값 미덮어쓰기)")
    void bulkUpsert_preservesOverviewAndHomepage() {
        int cid = 90003;
        // (a) 최초 적재 — DTO/매퍼가 overview/homepage를 안 쓰므로 null
        attractionDao.bulkUpsert(List.of(attraction(cid, "초기제목")));

        // (b) A-6 write-through가 채우는 상황을 모사
        jdbc.update("UPDATE attractions SET overview = 'OV', homepage = 'HP' WHERE content_id = ?", cid);

        // (c) 주간 배치가 같은 content_id를 title만 바꿔 재upsert
        attractionDao.bulkUpsert(List.of(attraction(cid, "배치갱신제목")));

        String title = jdbc.queryForObject(
                "SELECT title FROM attractions WHERE content_id = ?", String.class, cid);
        String overview = jdbc.queryForObject(
                "SELECT overview FROM attractions WHERE content_id = ?", String.class, cid);
        String homepage = jdbc.queryForObject(
                "SELECT homepage FROM attractions WHERE content_id = ?", String.class, cid);

        assertThat(title).isEqualTo("배치갱신제목");   // 배치 컬럼은 갱신
        assertThat(overview).isEqualTo("OV");          // 보존 (null로 안 날아감)
        assertThat(homepage).isEqualTo("HP");          // 보존
    }

    @Test
    @DisplayName("bulkUpsert: 좌표 null → geom null")
    void bulkUpsert_nullCoordsGivesNullGeom() {
        int cid = 90004;
        AttractionDto a = attraction(cid, "좌표없음");
        a.setLatitude(null);
        a.setLongitude(null);
        attractionDao.bulkUpsert(List.of(a));

        Boolean geomIsNull = jdbc.queryForObject(
                "SELECT geom IS NULL FROM attractions WHERE content_id = ?", Boolean.class, cid);
        assertThat(geomIsNull).isTrue();
    }

    @Test
    @DisplayName("bulkUpsert: modified_time 저장 + 더 최신 값으로 갱신")
    void bulkUpsert_modifiedTimePersistsAndUpdates() {
        int cid = 90005;
        LocalDateTime first = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
        AttractionDto a = attraction(cid, "시간테스트");
        a.setModifiedTime(first);
        attractionDao.bulkUpsert(List.of(a));

        LocalDateTime stored = jdbc.queryForObject(
                "SELECT modified_time FROM attractions WHERE content_id = ?",
                LocalDateTime.class, cid);
        assertThat(stored).isEqualTo(first);

        // 더 최신 modified_time으로 재upsert → 갱신
        LocalDateTime second = LocalDateTime.of(2026, 6, 15, 12, 30, 0);
        AttractionDto a2 = attraction(cid, "시간테스트");
        a2.setModifiedTime(second);
        attractionDao.bulkUpsert(List.of(a2));

        LocalDateTime updated = jdbc.queryForObject(
                "SELECT modified_time FROM attractions WHERE content_id = ?",
                LocalDateTime.class, cid);
        assertThat(updated).isEqualTo(second);
    }
}
