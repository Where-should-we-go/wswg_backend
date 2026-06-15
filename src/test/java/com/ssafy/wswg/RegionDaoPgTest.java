package com.ssafy.wswg;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ssafy.wswg.model.dao.RegionDao;
import com.ssafy.wswg.model.dto.GugunDto;
import com.ssafy.wswg.model.dto.SidoDto;

/**
 * RegionDao(MyBatis) upsert가 실제 PostgreSQL에서 동작하는지 검증.
 * insert / 멱등성(ON CONFLICT DO UPDATE) / 복합 PK(시군구)를 raw JdbcTemplate로 재조회해 확인.
 * 베이스 클래스의 @Transactional로 각 테스트는 롤백된다.
 */
class RegionDaoPgTest extends AbstractPostgisIntegrationTest {

    @Autowired
    RegionDao regionDao;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("upsertSidos: 행 삽입 + 멱등성(재upsert 행 수 불변, 이름 변경 반영)")
    void upsertSidos_insertAndIdempotent() {
        regionDao.upsertSidos(List.of(
                new SidoDto(91001, "테스트시도A"),
                new SidoDto(91002, "테스트시도B")));

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sidos WHERE sido_code IN (91001, 91002)", Integer.class);
        assertThat(count).isEqualTo(2);

        // 멱등성: 같은 code로 다시 upsert + 이름 변경 → 행 수 불변, 이름 갱신
        regionDao.upsertSidos(List.of(
                new SidoDto(91001, "테스트시도A-수정"),
                new SidoDto(91002, "테스트시도B")));

        Integer countAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sidos WHERE sido_code IN (91001, 91002)", Integer.class);
        assertThat(countAfter).isEqualTo(2);

        String name = jdbc.queryForObject(
                "SELECT sido_name FROM sidos WHERE sido_code = 91001", String.class);
        assertThat(name).isEqualTo("테스트시도A-수정");
    }

    @Test
    @DisplayName("upsertGuguns: FK/복합 PK 삽입 + 멱등성(재upsert 이름 갱신)")
    void upsertGuguns_insertAndIdempotent() {
        // 시군구는 sido_code FK 필요 → 부모 시도 먼저 삽입
        regionDao.upsertSidos(List.of(new SidoDto(91001, "테스트시도A")));

        regionDao.upsertGuguns(List.of(
                new GugunDto(91001, 91101, "테스트구1"),
                new GugunDto(91001, 91102, "테스트구2")));

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM guguns WHERE sido_code = 91001", Integer.class);
        assertThat(count).isEqualTo(2);

        // 멱등성: 같은 복합 PK로 재upsert + 이름 변경 → 행 수 불변, 이름 갱신
        regionDao.upsertGuguns(List.of(
                new GugunDto(91001, 91101, "테스트구1-수정")));

        Integer countAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM guguns WHERE sido_code = 91001", Integer.class);
        assertThat(countAfter).isEqualTo(2);

        String name = jdbc.queryForObject(
                "SELECT gugun_name FROM guguns WHERE sido_code = 91001 AND gugun_code = 91101",
                String.class);
        assertThat(name).isEqualTo("테스트구1-수정");
    }
}
