package com.ssafy.wswg.external.tour.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * detailIntro2(소개정보) 응답의 item. A-6는 이 중 휴무일(restdate*)만 캐시한다.
 * 휴무일 필드명은 콘텐츠 타입마다 다르므로(라이브 확인) 타입별 필드를 각각 보유하고
 * {@link #restDateFor(Integer)}로 골라낸다. 값은 자유 텍스트("매주 화요일", "연중무휴" 등).
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DetailIntroItem {

    private String contentid;
    private String contenttypeid;

    // 타입별 휴무일 필드(라이브 확인). 38 쇼핑의 restroom(화장실)과 혼동 금지 → 정확한 이름만 매핑.
    private String restdate;          // 12 관광지
    private String restdateculture;   // 14 문화시설
    private String restdateleports;   // 28 레포츠
    private String restdateshopping;  // 38 쇼핑
    private String restdatefood;      // 39 음식점

    /**
     * 휴무일(restdate*) 필드를 갖는 콘텐츠 타입인지. 축제(15)·여행코스(25)·숙박(32)은
     * 휴무일 개념이 없어(행사일·체크인시간 등 별개) false → 서비스가 detailIntro2를 호출하지 않는다.
     */
    public static boolean supportsRestDate(Integer contentTypeId) {
        if (contentTypeId == null) {
            return false;
        }
        return switch (contentTypeId) {
            case 12, 14, 28, 38, 39 -> true;
            default -> false;
        };
    }

    /** contentTypeId에 해당하는 휴무일 텍스트를 고른다. 휴무일 없는 타입이면 null. */
    public String restDateFor(Integer contentTypeId) {
        if (contentTypeId == null) {
            return null;
        }
        return switch (contentTypeId) {
            case 12 -> restdate;
            case 14 -> restdateculture;
            case 28 -> restdateleports;
            case 38 -> restdateshopping;
            case 39 -> restdatefood;
            default -> null;
        };
    }
}
