package com.ssafy.wswg.external.tour.dto;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * detailCommon2(공통정보) 응답의 item. A-6 상세는 이 중 overview/homepage만 캐시한다
 * (나머지 공통 필드는 이미 areaBasedList2 배치로 attractions에 적재됨).
 * 원본 JSON은 모든 값이 String이다.
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DetailCommonItem {

    /** href 추출용. <a href="URL" ...> 형태에서 URL만 뽑는다(쌍/홑따옴표 모두 허용). */
    private static final Pattern HREF =
            Pattern.compile("href\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    /** 평문 'label URL' 형태에서 http(s) URL을 뽑는다(앵커가 아닐 때 폴백). */
    private static final Pattern URL =
            Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);

    private String contentid;
    private String contenttypeid;
    private String title;
    private String overview;
    private String homepage;

    /**
     * homepage는 TourAPI가 두 가지 형태로 준다(라이브 확인):
     * <ol>
     *   <li>{@code <a href="URL" target="_blank" ...>label</a>} HTML 앵커</li>
     *   <li>{@code "비짓제주 https://www.visitjeju.net"} 같은 평문 'label URL'</li>
     * </ol>
     * JSON API 응답엔 깔끔한 URL이 자연스럽고 프론트 XSS 위험도 없으므로,
     * (1) 앵커면 href를, (2) 평문이면 첫 http(s) URL을 추출한다(라벨 제거).
     * URL을 못 찾으면 원문(trim)을 반환. 비어 있으면 null. HTML 엔티티는 디코드한다.
     */
    public String homepageUrl() {
        if (homepage == null) {
            return null;
        }
        String raw = homepage.trim();
        if (raw.isEmpty()) {
            return null;
        }
        Matcher anchor = HREF.matcher(raw);
        if (anchor.find()) {
            String url = unescapeHtml(anchor.group(1).trim());
            return url.isEmpty() ? null : url;
        }
        Matcher url = URL.matcher(raw);
        if (url.find()) {
            return unescapeHtml(url.group().trim());
        }
        return unescapeHtml(raw);
    }

    /**
     * href 안의 HTML 엔티티를 디코드한다. 앵커는 HTML이라 쿼리스트링의 {@code &}가 {@code &amp;}로
     * 이스케이프돼 오는 경우가 있어, 그대로 저장하면 링크가 깨진다. 흔한 엔티티만 처리하며,
     * {@code &amp;}를 마지막에 치환해 이중 디코드를 피한다.
     */
    private static String unescapeHtml(String s) {
        if (s.indexOf('&') < 0) {
            return s;
        }
        return s.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }
}
