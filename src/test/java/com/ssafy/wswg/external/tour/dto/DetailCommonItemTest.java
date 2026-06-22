package com.ssafy.wswg.external.tour.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DetailCommonItem.homepageUrl() — TourAPI homepage 앵커에서 href URL 추출 규칙. */
class DetailCommonItemTest {

    private DetailCommonItem withHomepage(String homepage) {
        DetailCommonItem item = new DetailCommonItem();
        item.setHomepage(homepage);
        return item;
    }

    @Test
    @DisplayName("앵커(쌍따옴표) → href URL만 추출")
    void anchorDoubleQuote() {
        assertThat(withHomepage(
                "<a href=\"https://tour.daegu.go.kr/index.do?menu_id=42\" target=\"_blank\">대구</a>")
                .homepageUrl())
                .isEqualTo("https://tour.daegu.go.kr/index.do?menu_id=42");
    }

    @Test
    @DisplayName("앵커(홑따옴표)도 추출")
    void anchorSingleQuote() {
        assertThat(withHomepage("<a href='http://example.com' target='_blank'>x</a>").homepageUrl())
                .isEqualTo("http://example.com");
    }

    @Test
    @DisplayName("href의 HTML 엔티티(&amp;) 디코드 → 쿼리스트링 복원")
    void decodesHtmlEntitiesInHref() {
        assertThat(withHomepage(
                "<a href=\"https://x.com/p?a=1&amp;b=2&amp;c=3\" target=\"_blank\">x</a>")
                .homepageUrl())
                .isEqualTo("https://x.com/p?a=1&b=2&c=3");
    }

    @Test
    @DisplayName("평문 'label URL'(라이브 형태) → URL만 추출, 라벨 제거")
    void plainLabelUrl() {
        // 라이브 확인된 형태: "비짓제주 https://www.visitjeju.net"
        assertThat(withHomepage("비짓제주 https://www.visitjeju.net").homepageUrl())
                .isEqualTo("https://www.visitjeju.net");
    }

    @Test
    @DisplayName("바로 URL이면 그대로(trim)")
    void bareUrl() {
        assertThat(withHomepage("  https://bare.example.com  ").homepageUrl())
                .isEqualTo("https://bare.example.com");
    }

    @Test
    @DisplayName("URL 패턴이 없으면 원문(trim) 반환")
    void noUrl() {
        assertThat(withHomepage("  홈페이지 없음  ").homepageUrl()).isEqualTo("홈페이지 없음");
    }

    @Test
    @DisplayName("null/빈 문자열 → null")
    void nullOrBlank() {
        assertThat(withHomepage(null).homepageUrl()).isNull();
        assertThat(withHomepage("").homepageUrl()).isNull();
        assertThat(withHomepage("   ").homepageUrl()).isNull();
    }
}
