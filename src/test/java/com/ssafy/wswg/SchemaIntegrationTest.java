package com.ssafy.wswg;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * schema.sql이 실제 PostgreSQL/PostGIS에 적용된 상태를 raw {@link JdbcTemplate}로 검증.
 *
 * <p>관광지/지역 INSERT는 매퍼(TripMapper/TripDao)가 이미 삭제됐으므로 JdbcTemplate로 직접 수행.
 */
class SchemaIntegrationTest extends AbstractPostgisIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    // ---------------------------------------------------------------
    // T2: 법정동 리네이밍 — attractions 컬럼명 검증
    // ---------------------------------------------------------------
    @Test
    @DisplayName("T2: attractions에 sido_code/gugun_code가 있고 area_code/si_gun_gu_code는 없다")
    void t2_columnRenaming() {
        List<String> columns = jdbc.queryForList(
                "select column_name from information_schema.columns " +
                        "where table_name = 'attractions'",
                String.class);

        assertThat(columns).contains("sido_code", "gugun_code");
        assertThat(columns).doesNotContain("area_code", "si_gun_gu_code");
    }

    // ---------------------------------------------------------------
    // T3: geom 자동 생성 + 좌표 순서(lng=X, lat=Y)
    // ---------------------------------------------------------------
    @Test
    @DisplayName("T3: lat/lng INSERT시 geom이 자동 생성되고 ST_Y=위도, ST_X=경도")
    void t3_geomGeneratedAndAxisOrder() {
        // sido_code/gugun_code는 null로 둬 guguns FK를 회피. geom은 GENERATED라 넣지 않는다.
        jdbc.update(
                "insert into attractions(content_id, title, content_type_id, latitude, longitude) " +
                        "values (?, ?, ?, ?, ?)",
                1, "t", 12, 35.1, 129.0);

        Double y = jdbc.queryForObject(
                "select ST_Y(geom) from attractions where content_id = 1", Double.class);
        Double x = jdbc.queryForObject(
                "select ST_X(geom) from attractions where content_id = 1", Double.class);

        assertThat(y).isCloseTo(35.1, org.assertj.core.data.Offset.offset(1e-9)); // 위도
        assertThat(x).isCloseTo(129.0, org.assertj.core.data.Offset.offset(1e-9)); // 경도
    }

    // ---------------------------------------------------------------
    // T4: 좌표 null이면 geom null, 에러 없음
    // ---------------------------------------------------------------
    @Test
    @DisplayName("T4: lat/lng null이면 geom도 null이고 INSERT 에러 없음")
    void t4_nullCoordinates() {
        jdbc.update(
                "insert into attractions(content_id, title, content_type_id, latitude, longitude) " +
                        "values (?, ?, ?, ?, ?)",
                2, "no-coords", 12, null, null);

        Object geom = jdbc.queryForMap(
                "select geom from attractions where content_id = 2").get("geom");
        assertThat(geom).isNull();
    }

    // ---------------------------------------------------------------
    // T5: FK 강제 (contenttypes FK, guguns 복합 FK)
    // ---------------------------------------------------------------
    @Test
    @DisplayName("T5a: 미존재 content_type_id=999 INSERT는 FK 위반으로 실패")
    void t5a_contentTypeFkEnforced() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into attractions(content_id, title, content_type_id, latitude, longitude) " +
                        "values (?, ?, ?, ?, ?)",
                901, "bad-ct", 999, 35.1, 129.0))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("T5b: guguns에 없는 (sido_code=11, gugun_code=110) INSERT는 복합 FK 위반으로 실패")
    void t5b_gugunFkEnforced() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into attractions(content_id, title, content_type_id, sido_code, gugun_code, latitude, longitude) " +
                        "values (?, ?, ?, ?, ?, ?, ?)",
                902, "bad-region", 12, 11, 110, 35.1, 129.0))
                .isInstanceOf(DataAccessException.class);
    }

    // ---------------------------------------------------------------
    // T6: contenttypes 시드 8종
    // ---------------------------------------------------------------
    @Test
    @DisplayName("T6: contenttypes 시드가 8종이고 {12,14,15,25,28,32,38,39}를 포함")
    void t6_contentTypeSeed() {
        Integer count = jdbc.queryForObject(
                "select count(*) from contenttypes", Integer.class);
        assertThat(count).isEqualTo(8);

        List<Integer> ids = jdbc.queryForList(
                "select content_type_id from contenttypes", Integer.class);
        assertThat(ids).contains(12, 14, 15, 25, 28, 32, 38, 39);
    }
}
