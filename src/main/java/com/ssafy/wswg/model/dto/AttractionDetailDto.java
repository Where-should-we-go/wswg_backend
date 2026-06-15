package com.ssafy.wswg.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 관광지 상세(S4 · A-6) 응답 DTO.
 * attractions의 전체 표시 필드 + sidos/guguns/contenttypes를 LEFT JOIN해 채운 이름 3종
 * (sidoName/gugunName/contentTypeName)을 담는다. 내부 PK(no)는 노출하지 않는다.
 *
 * <p>overview/homepage/restDate는 A-6 write-through로 채워지는 캐시 필드다.
 * restDate(휴무일)는 detailIntro2 응답의 타입별 restdate* 필드에서 온 자유 텍스트이며,
 * NULL=미조회, ''=조회완료·휴무정보없음을 뜻한다(서비스가 lazy fill).
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AttractionDetailDto {
    private Integer contentId;
    private String title;
    private Integer contentTypeId;
    private String contentTypeName;
    private Integer sidoCode;
    private String sidoName;
    private Integer gugunCode;
    private String gugunName;
    private String firstImage1;
    private String firstImage2;
    private Integer mapLevel;
    private Double latitude;
    private Double longitude;
    private String tel;
    private String addr1;
    private String addr2;
    private String homepage;
    private String overview;
    private String restDate;
    private java.time.LocalDateTime modifiedTime;
}
