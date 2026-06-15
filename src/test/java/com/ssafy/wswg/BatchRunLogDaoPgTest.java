package com.ssafy.wswg;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ssafy.wswg.model.dao.BatchRunLogDao;
import com.ssafy.wswg.model.dto.BatchRunLog;

/**
 * BatchRunLogDao.insertLog가 실제 PostgreSQL에 행을 남기는지 검증.
 * 베이스 클래스의 @Transactional로 각 테스트는 롤백된다.
 */
class BatchRunLogDaoPgTest extends AbstractPostgisIntegrationTest {

    @Autowired
    BatchRunLogDao batchRunLogDao;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("insertLog: 행 영속 + useGeneratedKeys로 id 채움")
    void insertLog_persists() {
        LocalDateTime started = LocalDateTime.of(2026, 6, 15, 3, 0, 0);
        LocalDateTime finished = LocalDateTime.of(2026, 6, 15, 3, 5, 0);
        BatchRunLog log = BatchRunLog.builder()
                .jobName("tour-load")
                .status("SUCCESS")
                .startedAt(started)
                .finishedAt(finished)
                .totalCount(50710)
                .attractionCount(50700)
                .sidoCount(17)
                .gugunCount(250)
                .skippedValidation(5)
                .skippedFk(5)
                .errorCode(null)
                .errorMessage(null)
                .build();

        int affected = batchRunLogDao.insertLog(log);
        assertThat(affected).isEqualTo(1);
        assertThat(log.getId()).isNotNull();

        String status = jdbc.queryForObject(
                "SELECT status FROM batch_run_log WHERE id = ?", String.class, log.getId());
        assertThat(status).isEqualTo("SUCCESS");

        Integer total = jdbc.queryForObject(
                "SELECT total_count FROM batch_run_log WHERE id = ?", Integer.class, log.getId());
        assertThat(total).isEqualTo(50710);

        Integer attraction = jdbc.queryForObject(
                "SELECT attraction_count FROM batch_run_log WHERE id = ?", Integer.class, log.getId());
        assertThat(attraction).isEqualTo(50700);
    }
}
