package com.ssafy.wswg.external.tour.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DetailIntroItem — 타입별 휴무일 필드 매핑(restDateFor) + supportsRestDate. */
class DetailIntroItemTest {

    @Test
    @DisplayName("supportsRestDate: 12/14/28/38/39만 true, 15/25/32·null은 false")
    void supportsRestDate() {
        assertThat(DetailIntroItem.supportsRestDate(12)).isTrue();
        assertThat(DetailIntroItem.supportsRestDate(14)).isTrue();
        assertThat(DetailIntroItem.supportsRestDate(28)).isTrue();
        assertThat(DetailIntroItem.supportsRestDate(38)).isTrue();
        assertThat(DetailIntroItem.supportsRestDate(39)).isTrue();
        assertThat(DetailIntroItem.supportsRestDate(15)).isFalse();
        assertThat(DetailIntroItem.supportsRestDate(25)).isFalse();
        assertThat(DetailIntroItem.supportsRestDate(32)).isFalse();
        assertThat(DetailIntroItem.supportsRestDate(null)).isFalse();
    }

    @Test
    @DisplayName("restDateFor: contentTypeId에 맞는 restdate* 필드를 고른다")
    void restDateForPicksByType() {
        DetailIntroItem item = new DetailIntroItem();
        item.setRestdate("관광지휴무");
        item.setRestdateculture("문화시설휴무");
        item.setRestdateleports("레포츠휴무");
        item.setRestdateshopping("쇼핑휴무");
        item.setRestdatefood("음식점휴무");

        assertThat(item.restDateFor(12)).isEqualTo("관광지휴무");
        assertThat(item.restDateFor(14)).isEqualTo("문화시설휴무");
        assertThat(item.restDateFor(28)).isEqualTo("레포츠휴무");
        assertThat(item.restDateFor(38)).isEqualTo("쇼핑휴무");
        assertThat(item.restDateFor(39)).isEqualTo("음식점휴무");
    }

    @Test
    @DisplayName("restDateFor: 휴무일 없는 타입(15/25/32)·null → null")
    void restDateForUnsupportedType() {
        DetailIntroItem item = new DetailIntroItem();
        item.setRestdate("관광지휴무");
        assertThat(item.restDateFor(15)).isNull();
        assertThat(item.restDateFor(32)).isNull();
        assertThat(item.restDateFor(null)).isNull();
    }
}
