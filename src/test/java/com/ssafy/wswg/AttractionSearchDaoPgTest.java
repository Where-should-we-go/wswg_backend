package com.ssafy.wswg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ssafy.wswg.model.dao.AttractionDao;
import com.ssafy.wswg.model.dto.AttractionDto;
import com.ssafy.wswg.model.dto.AttractionSearchCondition;
import com.ssafy.wswg.model.dto.AttractionSummaryDto;

/**
 * AttractionDao.search / countSearch 검증.
 * 동적 필터(시도/구군/콘텐츠타입/키워드) + LEFT JOIN(코드 null 관광지 포함) + ILIKE(대소문자 무시) +
 * 페이징(LIMIT/OFFSET) + 빈 결과를 실제 PostgreSQL에서 확인한다.
 * 베이스 클래스의 @Transactional로 각 테스트는 롤백된다.
 */
class AttractionSearchDaoPgTest extends AbstractPostgisIntegrationTest {

    @Autowired
    AttractionDao attractionDao;

    @Autowired
    JdbcTemplate jdbc;

    // content_id: A 경복궁(서울/종로/12), B 영문(서울/종로/14), C 해운대(부산/구군null/12), D 코드없음(전부 null)
    static final int A = 90101;
    static final int B = 90102;
    static final int C = 90103;
    static final int D = 90104;

    @BeforeEach
    void setUp() {
        // FK 대상 지역 마스터. @Transactional 롤백되므로 안전하게 ON CONFLICT.
        jdbc.update("INSERT INTO sidos (sido_code, sido_name) VALUES (11, '서울') "
                + "ON CONFLICT (sido_code) DO NOTHING");
        jdbc.update("INSERT INTO sidos (sido_code, sido_name) VALUES (26, '부산') "
                + "ON CONFLICT (sido_code) DO NOTHING");
        jdbc.update("INSERT INTO guguns (sido_code, gugun_code, gugun_name) VALUES (11, 110, '종로구') "
                + "ON CONFLICT (sido_code, gugun_code) DO NOTHING");

        // 삽입 순서 = no(BIGSERIAL) 오름차순 = 페이징 기대 순서 A,B,C,D
        attractionDao.bulkUpsert(List.of(
                attraction(A, "경복궁", 12, 11, 110),
                attraction(B, "Gyeongbokgung Palace", 14, 11, 110),
                attraction(C, "해운대", 12, 26, null),
                attraction(D, "코드없는관광지", null, null, null)));
    }

    private AttractionDto attraction(int contentId, String title, Integer ctype, Integer sido, Integer gugun) {
        AttractionDto a = new AttractionDto();
        a.setContentId(contentId);
        a.setTitle(title);
        a.setContentTypeId(ctype);
        a.setSidoCode(sido);
        a.setGugunCode(gugun);
        a.setFirstImage1("https://img/" + contentId + ".jpg");
        return a;
    }

    private AttractionSearchCondition cond(Integer sido, Integer gugun, List<Integer> ctypes, String keyword) {
        return new AttractionSearchCondition(sido, gugun, ctypes, keyword, false);
    }

    private List<AttractionSummaryDto> search(AttractionSearchCondition c) {
        return attractionDao.search(c, 100, 0);
    }

    @Test
    @DisplayName("시도 필터: sidoCode=11 → 서울 관광지만(A,B)")
    void search_bySido() {
        AttractionSearchCondition c = cond(11, null, null, null);
        assertThat(search(c)).extracting(AttractionSummaryDto::getContentId)
                .containsExactlyInAnyOrder(A, B);
        assertThat(attractionDao.countSearch(c)).isEqualTo(2);
    }

    @Test
    @DisplayName("콘텐츠타입 다중 IN: [12,14] → A,B,C / [12] → A,C")
    void search_byContentTypeIn() {
        AttractionSearchCondition multi = cond(null, null, List.of(12, 14), null);
        assertThat(search(multi)).extracting(AttractionSummaryDto::getContentId)
                .containsExactlyInAnyOrder(A, B, C);
        assertThat(attractionDao.countSearch(multi)).isEqualTo(3);

        AttractionSearchCondition single = cond(null, null, List.of(12), null);
        assertThat(search(single)).extracting(AttractionSummaryDto::getContentId)
                .containsExactlyInAnyOrder(A, C);
        assertThat(attractionDao.countSearch(single)).isEqualTo(2);
    }

    @Test
    @DisplayName("키워드 ILIKE: 'gyeongbok'(소문자) → 영문 제목 B 매칭(대소문자 무시)")
    void search_byKeyword_caseInsensitive() {
        AttractionSearchCondition c = cond(null, null, null, "gyeongbok");
        assertThat(search(c)).extracting(AttractionSummaryDto::getContentId)
                .containsExactly(B);
        assertThat(attractionDao.countSearch(c)).isEqualTo(1);
    }

    @Test
    @DisplayName("키워드 한글 부분일치: '해운' → C")
    void search_byKeyword_korean() {
        AttractionSearchCondition c = cond(null, null, null, "해운");
        assertThat(search(c)).extracting(AttractionSummaryDto::getContentId)
                .containsExactly(C);
    }

    @Test
    @DisplayName("복합 필터 AND: sidoCode=11 + keyword='경복' → A만")
    void search_combinedFilters() {
        AttractionSearchCondition c = cond(11, null, null, "경복");
        assertThat(search(c)).extracting(AttractionSummaryDto::getContentId)
                .containsExactly(A);
    }

    @Test
    @DisplayName("LEFT JOIN: 필터 없음 → 코드 null 관광지(D)도 포함, 지역명은 null로 채움")
    void search_noFilter_leftJoinIncludesNullCodes() {
        List<AttractionSummaryDto> all = search(cond(null, null, null, null));

        assertThat(all).extracting(AttractionSummaryDto::getContentId)
                .containsExactlyInAnyOrder(A, B, C, D);
        assertThat(attractionDao.countSearch(cond(null, null, null, null))).isEqualTo(4);

        // 지역명 매핑 확인: A=서울/종로구, C=부산/구군null, D=둘 다 null
        assertThat(all)
                .extracting(AttractionSummaryDto::getContentId,
                        AttractionSummaryDto::getSidoName,
                        AttractionSummaryDto::getGugunName)
                .contains(
                        tuple(A, "서울", "종로구"),
                        tuple(C, "부산", null),     // gugun_code null → guguns LEFT JOIN 미매칭
                        tuple(D, null, null));      // sido_code null → sidos LEFT JOIN 미매칭
    }

    @Test
    @DisplayName("페이징: ORDER BY no 안정 정렬, limit/offset로 페이지 분할")
    void search_paging() {
        AttractionSearchCondition c = cond(null, null, null, null);

        assertThat(attractionDao.search(c, 2, 0))
                .extracting(AttractionSummaryDto::getContentId)
                .containsExactly(A, B);
        assertThat(attractionDao.search(c, 2, 2))
                .extracting(AttractionSummaryDto::getContentId)
                .containsExactly(C, D);
    }

    @Test
    @DisplayName("구군 필터는 시도와 함께만 적용: 다른 시도의 동일 구군코드를 과매칭하지 않음")
    void search_gugunRequiresSido() {
        // 부산(26)에도 같은 구군코드 110 + 그 지역 관광지(E) 추가
        jdbc.update("INSERT INTO guguns (sido_code, gugun_code, gugun_name) VALUES (26, 110, '부산구110') "
                + "ON CONFLICT (sido_code, gugun_code) DO NOTHING");
        int e = 90105;
        attractionDao.bulkUpsert(List.of(attraction(e, "부산110관광지", 12, 26, 110)));

        // sido+gugun → 서울 110만(A,B), 부산 110(E)은 제외
        assertThat(search(cond(11, 110, null, null)))
                .extracting(AttractionSummaryDto::getContentId)
                .containsExactlyInAnyOrder(A, B);

        // gugun만(sido 없음) → 과매칭 방지를 위해 gugun 필터 미적용(전체 반환)
        assertThat(search(cond(null, 110, null, null)))
                .extracting(AttractionSummaryDto::getContentId)
                .containsExactlyInAnyOrder(A, B, C, D, e);
    }

    @Test
    @DisplayName("키워드 와일드카드 이스케이프: '50%'는 리터럴로 매칭(다른 행 과매칭 안 함)")
    void search_keywordWildcardEscaped() {
        attractionDao.bulkUpsert(List.of(
                attraction(90106, "50% 할인 명소", 12, 11, 110),
                attraction(90107, "5000원 식당", 12, 11, 110)));

        // '%'가 와일드카드로 처리되면 '5000원 식당'도 매칭되지만, 이스케이프되어 '50%' 리터럴만 매칭
        assertThat(search(cond(null, null, null, "50%")))
                .extracting(AttractionSummaryDto::getContentId)
                .containsExactly(90106);
    }

    @Test
    @DisplayName("결과 없음: 매칭 없는 키워드 → 빈 목록 + count 0")
    void search_noMatch_returnsEmpty() {
        AttractionSearchCondition c = cond(null, null, null, "존재하지않는장소zzz");
        assertThat(search(c)).isEmpty();
        assertThat(attractionDao.countSearch(c)).isEqualTo(0);
    }
}
