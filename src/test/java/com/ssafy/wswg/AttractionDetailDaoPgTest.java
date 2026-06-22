package com.ssafy.wswg;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ssafy.wswg.model.dao.AttractionDao;
import com.ssafy.wswg.model.dto.AttractionDetailDto;
import com.ssafy.wswg.model.dto.AttractionDto;

/**
 * AttractionDao.selectDetailByContentId(A-6) 검증.
 * sidos/guguns/contenttypes LEFT JOIN으로 이름 3종을 채우는지, 코드 null인 관광지도 누락 없이
 * 반환하는지, write-through 캐시 컬럼(overview/homepage/rest_date)을 그대로 읽는지,
 * 없는 content_id면 null인지를 실제 PostgreSQL에서 확인한다.
 * 베이스 클래스의 @Transactional로 각 테스트는 롤백된다(contenttypes는 init 시드, 트랜잭션 밖).
 */
class AttractionDetailDaoPgTest extends AbstractPostgisIntegrationTest {

    @Autowired
    AttractionDao attractionDao;

    @Autowired
    JdbcTemplate jdbc;

    // A 경복궁(서울11/종로110/관광지12, 캐시 채움), B 코드없음(전부 null, 캐시 미채움)
    static final int A = 90201;
    static final int B = 90202;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO sidos (sido_code, sido_name) VALUES (11, '서울') "
                + "ON CONFLICT (sido_code) DO NOTHING");
        jdbc.update("INSERT INTO guguns (sido_code, gugun_code, gugun_name) VALUES (11, 110, '종로구') "
                + "ON CONFLICT (sido_code, gugun_code) DO NOTHING");
    }

    private AttractionDto attraction(int contentId, String title, Integer ctype, Integer sido, Integer gugun) {
        AttractionDto a = new AttractionDto();
        a.setContentId(contentId);
        a.setTitle(title);
        a.setContentTypeId(ctype);
        a.setSidoCode(sido);
        a.setGugunCode(gugun);
        a.setFirstImage1("https://img/" + contentId + "_1.jpg");
        a.setFirstImage2("https://img/" + contentId + "_2.jpg");
        a.setTel("02-1234-5678");
        a.setAddr1("서울특별시 종로구 사직로 161");
        a.setAddr2("(세종로)");
        a.setLatitude(37.5759);
        a.setLongitude(126.9769);
        return a;
    }

    @Test
    @DisplayName("LEFT JOIN: 코드가 다 있으면 sidoName/gugunName/contentTypeName 채움 + 표시 필드 매핑")
    void selectDetail_joinsNames() {
        attractionDao.bulkUpsert(List.of(attraction(A, "경복궁", 12, 11, 110)));

        AttractionDetailDto d = attractionDao.selectDetailByContentId(A);

        assertThat(d).isNotNull();
        assertThat(d.getContentId()).isEqualTo(A);
        assertThat(d.getTitle()).isEqualTo("경복궁");
        assertThat(d.getContentTypeId()).isEqualTo(12);
        assertThat(d.getContentTypeName()).isEqualTo("관광지");   // contenttypes init 시드
        assertThat(d.getSidoCode()).isEqualTo(11);
        assertThat(d.getSidoName()).isEqualTo("서울");
        assertThat(d.getGugunCode()).isEqualTo(110);
        assertThat(d.getGugunName()).isEqualTo("종로구");
        assertThat(d.getFirstImage1()).isEqualTo("https://img/" + A + "_1.jpg");
        assertThat(d.getFirstImage2()).isEqualTo("https://img/" + A + "_2.jpg");
        assertThat(d.getTel()).isEqualTo("02-1234-5678");
        assertThat(d.getAddr1()).isEqualTo("서울특별시 종로구 사직로 161");
        assertThat(d.getLatitude()).isEqualTo(37.5759);
        assertThat(d.getLongitude()).isEqualTo(126.9769);
    }

    @Test
    @DisplayName("write-through 캐시 컬럼(overview/homepage/rest_date)을 그대로 읽는다")
    void selectDetail_readsCacheColumns() {
        attractionDao.bulkUpsert(List.of(attraction(A, "경복궁", 12, 11, 110)));
        // bulkUpsert는 이 3컬럼을 안 쓰므로 NULL. A-6 write-through가 채우는 상황을 모사.
        jdbc.update("UPDATE attractions SET overview = ?, homepage = ?, rest_date = ? "
                + "WHERE content_id = ?", "조선의 법궁", "http://royalpalace.go.kr", "매주 화요일", A);

        AttractionDetailDto d = attractionDao.selectDetailByContentId(A);

        assertThat(d.getOverview()).isEqualTo("조선의 법궁");
        assertThat(d.getHomepage()).isEqualTo("http://royalpalace.go.kr");
        assertThat(d.getRestDate()).isEqualTo("매주 화요일");
    }

    @Test
    @DisplayName("미채움 캐시 컬럼은 null로 반환(lazy fill 트리거 신호)")
    void selectDetail_nullCacheColumns() {
        attractionDao.bulkUpsert(List.of(attraction(A, "경복궁", 12, 11, 110)));

        AttractionDetailDto d = attractionDao.selectDetailByContentId(A);

        assertThat(d.getOverview()).isNull();
        assertThat(d.getHomepage()).isNull();
        assertThat(d.getRestDate()).isNull();
    }

    @Test
    @DisplayName("LEFT JOIN: 코드가 모두 null인 관광지도 반환, 이름 3종은 null")
    void selectDetail_nullCodesStillReturned() {
        attractionDao.bulkUpsert(List.of(attraction(B, "코드없는관광지", null, null, null)));

        AttractionDetailDto d = attractionDao.selectDetailByContentId(B);

        assertThat(d).isNotNull();
        assertThat(d.getContentId()).isEqualTo(B);
        assertThat(d.getTitle()).isEqualTo("코드없는관광지");
        assertThat(d.getContentTypeName()).isNull();
        assertThat(d.getSidoName()).isNull();
        assertThat(d.getGugunName()).isNull();
    }

    @Test
    @DisplayName("없는 content_id → null")
    void selectDetail_notFoundReturnsNull() {
        assertThat(attractionDao.selectDetailByContentId(99999999)).isNull();
    }

    @Test
    @DisplayName("updateOverviewCache: overview/homepage만 갱신, 다른 컬럼·rest_date 보존")
    void updateOverviewCache_updatesOnlyOverviewAndHomepage() {
        attractionDao.bulkUpsert(List.of(attraction(A, "경복궁", 12, 11, 110)));
        // rest_date가 이미 채워져 있어도 overview 캐시가 건드리지 않아야 함
        jdbc.update("UPDATE attractions SET rest_date = ? WHERE content_id = ?", "매주 화요일", A);

        int updated = attractionDao.updateOverviewCache(A, "조선의 법궁", "http://royalpalace.go.kr");

        assertThat(updated).isEqualTo(1);
        AttractionDetailDto d = attractionDao.selectDetailByContentId(A);
        assertThat(d.getOverview()).isEqualTo("조선의 법궁");
        assertThat(d.getHomepage()).isEqualTo("http://royalpalace.go.kr");
        // 보존 확인
        assertThat(d.getTitle()).isEqualTo("경복궁");
        assertThat(d.getTel()).isEqualTo("02-1234-5678");
        assertThat(d.getRestDate()).isEqualTo("매주 화요일");
    }

    @Test
    @DisplayName("updateOverviewCache: overview null → '' 센티넬 저장(미조회 NULL과 구분, 재호출 방지)")
    void updateOverviewCache_nullOverviewStoresEmptySentinel() {
        attractionDao.bulkUpsert(List.of(attraction(A, "경복궁", 12, 11, 110)));

        attractionDao.updateOverviewCache(A, null, null);

        AttractionDetailDto d = attractionDao.selectDetailByContentId(A);
        assertThat(d.getOverview()).isEmpty();   // NULL 아님 → lazy fill 재트리거 안 됨
        assertThat(d.getHomepage()).isNull();    // homepage는 트리거 아님 → NULL 허용
    }

    @Test
    @DisplayName("updateOverviewCache: 빈 응답이 이미 캐시된 overview/homepage를 덮어쓰지 않음(재동기화 방어)")
    void updateOverviewCache_emptyDoesNotOverwriteExisting() {
        attractionDao.bulkUpsert(List.of(attraction(A, "경복궁", 12, 11, 110)));
        attractionDao.updateOverviewCache(A, "조선의 법궁", "http://royalpalace.go.kr");  // 최초 채움

        attractionDao.updateOverviewCache(A, null, null);  // 재동기화가 빈 응답으로 호출

        AttractionDetailDto d = attractionDao.selectDetailByContentId(A);
        assertThat(d.getOverview()).isEqualTo("조선의 법궁");           // 보존(데이터 손실 없음)
        assertThat(d.getHomepage()).isEqualTo("http://royalpalace.go.kr");  // 보존
    }

    @Test
    @DisplayName("updateRestDateCache: 휴무일만 갱신, 다른 컬럼(overview 등) 보존")
    void updateRestDateCache_updatesOnlyRestDate() {
        attractionDao.bulkUpsert(List.of(attraction(A, "경복궁", 12, 11, 110)));
        jdbc.update("UPDATE attractions SET overview = ? WHERE content_id = ?", "조선의 법궁", A);

        int updated = attractionDao.updateRestDateCache(A, "매주 화요일");

        assertThat(updated).isEqualTo(1);
        AttractionDetailDto d = attractionDao.selectDetailByContentId(A);
        assertThat(d.getRestDate()).isEqualTo("매주 화요일");
        assertThat(d.getOverview()).isEqualTo("조선의 법궁");   // 보존
        assertThat(d.getTitle()).isEqualTo("경복궁");
    }

    @Test
    @DisplayName("updateRestDateCache: 빈 휴무일 → '' 센티넬(미조회 NULL과 구분, 재호출 방지)")
    void updateRestDateCache_emptyStoresSentinel() {
        attractionDao.bulkUpsert(List.of(attraction(A, "경복궁", 12, 11, 110)));

        attractionDao.updateRestDateCache(A, null);   // 휴무일 없는 타입/빈 응답 모사

        AttractionDetailDto d = attractionDao.selectDetailByContentId(A);
        assertThat(d.getRestDate()).isEmpty();   // NULL 아님 → 재트리거 안 됨
    }

    @Test
    @DisplayName("updateRestDateCache: 빈 값이 기존 휴무일을 덮어쓰지 않음(재동기화 방어)")
    void updateRestDateCache_emptyDoesNotOverwriteExisting() {
        attractionDao.bulkUpsert(List.of(attraction(A, "경복궁", 12, 11, 110)));
        attractionDao.updateRestDateCache(A, "매주 화요일");

        attractionDao.updateRestDateCache(A, null);

        AttractionDetailDto d = attractionDao.selectDetailByContentId(A);
        assertThat(d.getRestDate()).isEqualTo("매주 화요일");   // 보존
    }
}
