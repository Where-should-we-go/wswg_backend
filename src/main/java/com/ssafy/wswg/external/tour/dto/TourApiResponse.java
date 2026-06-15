package com.ssafy.wswg.external.tour.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * KorService2 공통 응답 래퍼.
 * <pre>
 * {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
 *  "body":{"items":{"item":[ ... ]},"numOfRows":3,"pageNo":1,"totalCount":17}}}
 * </pre>
 *
 * <p>빈 결과일 때 {@code "items":""}(빈 문자열)로 내려오는 트랩이 있으므로,
 * ObjectMapper에 {@code ACCEPT_EMPTY_STRING_AS_NULL_OBJECT}를 켜서
 * {@link Body#items}가 null이 되도록 한다. 단건일 때 {@code item}이 배열이 아닌
 * 객체로 오는 트랩은 {@code ACCEPT_SINGLE_VALUE_AS_ARRAY}로 처리한다.
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TourApiResponse<T> {

    private Response<T> response;

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response<T> {
        private Header header;
        private Body<T> body;
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Header {
        private String resultCode;
        private String resultMsg;
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body<T> {
        /** 빈 결과면 JSON상 ""로 와서 null로 역직렬화된다. */
        private Items<T> items;
        private int numOfRows;
        private int pageNo;
        private int totalCount;
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Items<T> {
        private List<T> item;
    }

    /** resultCode를 안전하게 꺼낸다. 구조가 비면 null. */
    public String resultCode() {
        return response != null && response.header != null ? response.header.resultCode : null;
    }

    /** resultMsg를 안전하게 꺼낸다. */
    public String resultMsg() {
        return response != null && response.header != null ? response.header.resultMsg : null;
    }

    /** item 리스트를 항상 non-null로 반환한다(빈 결과/단건 트랩 흡수). */
    public List<T> itemList() {
        if (response == null || response.body == null
                || response.body.items == null || response.body.items.item == null) {
            return List.of();
        }
        return response.body.items.item;
    }

    /** body가 없으면 0. */
    public int totalCount() {
        return response != null && response.body != null ? response.body.totalCount : 0;
    }

    public int pageNo() {
        return response != null && response.body != null ? response.body.pageNo : 0;
    }

    public int numOfRows() {
        return response != null && response.body != null ? response.body.numOfRows : 0;
    }
}
