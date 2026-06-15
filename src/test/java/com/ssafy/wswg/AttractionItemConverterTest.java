package com.ssafy.wswg;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ssafy.wswg.model.dto.AttractionDto;
import com.ssafy.wswg.tour.AttractionItemConverter;
import com.ssafy.wswg.tour.AttractionItemConverter.ConvertResult;
import com.ssafy.wswg.tour.dto.AreaBasedItem;

/**
 * AttractionItemConverter 정제/검증 규칙 단위 테스트(Docker 불필요).
 */
class AttractionItemConverterTest {

    private final AttractionItemConverter converter = new AttractionItemConverter();

    private static final Set<Integer> VALID_SIDOS = Set.of(11, 26);
    private static final Set<String> VALID_GUGUNS = Set.of("11-110", "11-140", "26-110");
    private static final Set<Integer> VALID_TYPES = Set.of(12, 14, 15, 25, 28, 32, 38, 39);

    /** 모든 필드가 채워진 정상 item을 베이스로 만든다. */
    private AreaBasedItem validItem() {
        AreaBasedItem i = new AreaBasedItem();
        i.setContentid("126508");
        i.setTitle("경복궁");
        i.setContenttypeid("12");
        i.setLDongRegnCd("11");
        i.setLDongSignguCd("110");
        i.setMapx("126.9769");
        i.setMapy("37.5796");
        i.setMlevel("6");
        i.setFirstimage("http://img/1.jpg");
        i.setFirstimage2("http://img/2.jpg");
        i.setTel("02-1234-5678");
        i.setAddr1("서울 종로구");
        i.setAddr2("세종로 1");
        i.setModifiedtime("20240115093000");
        return i;
    }

    private ConvertResult convert(AreaBasedItem... items) {
        return converter.convert(List.of(items), VALID_SIDOS, VALID_GUGUNS, VALID_TYPES);
    }

    @Test
    @DisplayName("정상 item: 모든 필드 매핑")
    void fullyValidItem_mapsAllFields() {
        ConvertResult r = convert(validItem());

        assertThat(r.skippedValidation()).isZero();
        assertThat(r.skippedFk()).isZero();
        assertThat(r.attractions()).hasSize(1);

        AttractionDto d = r.attractions().get(0);
        assertThat(d.getContentId()).isEqualTo(126508);
        assertThat(d.getTitle()).isEqualTo("경복궁");
        assertThat(d.getContentTypeId()).isEqualTo(12);
        assertThat(d.getSidoCode()).isEqualTo(11);
        assertThat(d.getGugunCode()).isEqualTo(110);
        assertThat(d.getMapLevel()).isEqualTo(6);
        assertThat(d.getLongitude()).isEqualTo(126.9769);
        assertThat(d.getLatitude()).isEqualTo(37.5796);
        assertThat(d.getFirstImage1()).isEqualTo("http://img/1.jpg");
        assertThat(d.getFirstImage2()).isEqualTo("http://img/2.jpg");
        assertThat(d.getTel()).isEqualTo("02-1234-5678");
        assertThat(d.getAddr1()).isEqualTo("서울 종로구");
        assertThat(d.getAddr2()).isEqualTo("세종로 1");
        assertThat(d.getModifiedTime()).isEqualTo(LocalDateTime.of(2024, 1, 15, 9, 30, 0));
        // list API에 없는 필드는 null
        assertThat(d.getHomepage()).isNull();
        assertThat(d.getOverview()).isNull();
    }

    @Test
    @DisplayName("contentId 비거나 숫자 아님 → 행 스킵(skippedValidation++)")
    void blankOrNonNumericContentId_skips() {
        AreaBasedItem blank = validItem();
        blank.setContentid("");
        AreaBasedItem nonNum = validItem();
        nonNum.setContentid("abc");

        ConvertResult r = convert(blank, nonNum);
        assertThat(r.attractions()).isEmpty();
        assertThat(r.skippedValidation()).isEqualTo(2);
    }

    @Test
    @DisplayName("title 비어 있음 → 행 스킵(skippedValidation++)")
    void blankTitle_skips() {
        AreaBasedItem i = validItem();
        i.setTitle("   ");
        ConvertResult r = convert(i);
        assertThat(r.attractions()).isEmpty();
        assertThat(r.skippedValidation()).isEqualTo(1);
    }

    @Test
    @DisplayName("contentType 미지원 → null (스킵 아님)")
    void unknownContentType_nulled() {
        AreaBasedItem i = validItem();
        i.setContenttypeid("99");
        ConvertResult r = convert(i);
        assertThat(r.attractions()).hasSize(1);
        assertThat(r.attractions().get(0).getContentTypeId()).isNull();
        assertThat(r.skippedValidation()).isZero();
    }

    @Test
    @DisplayName("FK 미스: 잘못된 gugun 키 → gugun만 null + skippedFk")
    void badGugunKey_nullsGugunOnly() {
        AreaBasedItem i = validItem();
        i.setLDongRegnCd("11");      // 유효 sido
        i.setLDongSignguCd("999");   // "11-999" 없음
        ConvertResult r = convert(i);
        AttractionDto d = r.attractions().get(0);
        assertThat(d.getSidoCode()).isEqualTo(11);
        assertThat(d.getGugunCode()).isNull();
        assertThat(r.skippedFk()).isEqualTo(1);
    }

    @Test
    @DisplayName("FK 미스: 잘못된 sido → 둘 다 null + skippedFk")
    void badSido_nullsBoth() {
        AreaBasedItem i = validItem();
        i.setLDongRegnCd("99");      // 유효 sido 아님
        i.setLDongSignguCd("110");
        ConvertResult r = convert(i);
        AttractionDto d = r.attractions().get(0);
        assertThat(d.getSidoCode()).isNull();
        assertThat(d.getGugunCode()).isNull();
        // gugun 키 "99-110"도 없으니 한 번 카운트, sido 위반은 중복 카운트 안 함 → 1
        assertThat(r.skippedFk()).isEqualTo(1);
    }

    @Test
    @DisplayName("좌표: bbox 밖 / 0 / 빈 값 → lat·lng 둘 다 null")
    void badCoords_nulled() {
        AreaBasedItem outOfBox = validItem();
        outOfBox.setMapx("0.0");        // 0
        outOfBox.setMapy("37.5");
        AreaBasedItem outside = validItem();
        outside.setMapx("139.0");        // bbox 밖(일본)
        outside.setMapy("35.0");
        AreaBasedItem blank = validItem();
        blank.setMapx("");
        blank.setMapy("");

        for (AreaBasedItem i : List.of(outOfBox, outside, blank)) {
            ConvertResult r = convert(i);
            AttractionDto d = r.attractions().get(0);
            assertThat(d.getLatitude()).as("lat null").isNull();
            assertThat(d.getLongitude()).as("lng null").isNull();
        }
    }

    @Test
    @DisplayName("이미지: http로 시작 안 하면 null")
    void nonHttpImage_nulled() {
        AreaBasedItem i = validItem();
        i.setFirstimage("");
        i.setFirstimage2("ftp://x");
        ConvertResult r = convert(i);
        AttractionDto d = r.attractions().get(0);
        assertThat(d.getFirstImage1()).isNull();
        assertThat(d.getFirstImage2()).isNull();
    }

    @Test
    @DisplayName("modifiedtime: 정상 파싱 + 깨진 값 → null")
    void modifiedTime_parseAndBad() {
        AreaBasedItem ok = validItem();
        ok.setModifiedtime("20231231235959");
        assertThat(convert(ok).attractions().get(0).getModifiedTime())
                .isEqualTo(LocalDateTime.of(2023, 12, 31, 23, 59, 59));

        AreaBasedItem bad = validItem();
        bad.setModifiedtime("not-a-date");
        assertThat(convert(bad).attractions().get(0).getModifiedTime()).isNull();

        AreaBasedItem empty = validItem();
        empty.setModifiedtime("");
        assertThat(convert(empty).attractions().get(0).getModifiedTime()).isNull();
    }

    @Test
    @DisplayName("길이 초과: title>255 / tel>100 은 절단(전체 트랜잭션 깨짐 방지)")
    void overLongValues_truncated() {
        AreaBasedItem i = validItem();
        i.setTitle("가".repeat(300));   // 255 초과
        i.setTel("0".repeat(150));      // 100 초과

        AttractionDto d = convert(i).attractions().get(0);
        assertThat(d.getTitle()).hasSize(255);
        assertThat(d.getTel()).hasSize(100);
    }
}
