package com.ssafy.wswg.external.tour;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.ssafy.wswg.model.dto.AttractionDto;
import com.ssafy.wswg.external.tour.dto.AreaBasedItem;

/**
 * areaBasedList2의 {@link AreaBasedItem}(전부 String)을 정제·검증해
 * {@link AttractionDto}로 변환한다.
 *
 * <p>이 변환은 순수 함수다(HTTP/DB 접근 없음). 유효한 region/contentType 세트는
 * 호출부(orchestrator)가 DB에서 미리 읽어 인자로 넘긴다. 그래서 행당 FK 조회 없이
 * 인메모리 세트로 FK 위반을 걸러낸다.
 *
 * <p>검증 정책:
 * <ul>
 *   <li>contentId 비거나 숫자 아님 → 행 스킵(skippedValidation++).</li>
 *   <li>title 비어 있음(NOT NULL) → 행 스킵(skippedValidation++).</li>
 *   <li>contentTypeId가 유효 세트에 없으면 null(FK nullable; 스킵 아님).</li>
 *   <li>sido/gugun FK 위반 → 해당 코드 null 처리(skippedFk++).</li>
 *   <li>좌표가 비거나 0/NaN/한국 bbox 밖이면 lat/lng 둘 다 null(geom null).</li>
 *   <li>이미지가 http로 시작 안 하면 null.</li>
 *   <li>modifiedtime "yyyyMMddHHmmss" 파싱 실패면 null.</li>
 *   <li>homepage/overview는 list API에 없으므로 null로 둔다(upsert가 보존).</li>
 * </ul>
 */
@Component
public class AttractionItemConverter {

    private static final DateTimeFormatter MODIFIED_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // 한국 영역 bounding box. 이 밖의 좌표는 오염으로 보고 버린다.
    private static final double LAT_MIN = 33.0;
    private static final double LAT_MAX = 39.0;
    private static final double LON_MIN = 124.0;
    private static final double LON_MAX = 132.0;

    // DB 컬럼 길이 한도(VARCHAR). 초과 값은 절단한다(전체 트랜잭션이 깨지는 것 방지).
    private static final int TITLE_MAX = 255;   // attractions.title VARCHAR(255)
    private static final int TEL_MAX = 100;     // attractions.tel   VARCHAR(100)

    /**
     * 변환 결과: 유효 DTO 목록 + 스킵 카운트.
     */
    public record ConvertResult(List<AttractionDto> attractions, int skippedValidation, int skippedFk) {
    }

    /**
     * @param items                원본 item 목록
     * @param validSidoCodes       DB에 존재하는 시도 code 세트
     * @param validGugunKeys       "sido-gugun" 형식의 유효 시군구 키 세트
     * @param validContentTypeIds  유효 contentType id 세트
     */
    public ConvertResult convert(List<AreaBasedItem> items,
            Set<Integer> validSidoCodes,
            Set<String> validGugunKeys,
            Set<Integer> validContentTypeIds) {

        List<AttractionDto> out = new ArrayList<>();
        int skippedValidation = 0;
        int skippedFk = 0;

        if (items == null) {
            return new ConvertResult(out, 0, 0);
        }

        for (AreaBasedItem item : items) {
            // contentId: 필수 + 숫자
            Integer contentId = parseIntOrNull(item.getContentid());
            if (contentId == null) {
                skippedValidation++;
                continue;
            }

            // title: NOT NULL
            String title = item.getTitle();
            if (isBlank(title)) {
                skippedValidation++;
                continue;
            }

            AttractionDto dto = new AttractionDto();
            dto.setContentId(contentId);
            dto.setTitle(truncate(title.trim(), TITLE_MAX));

            // contentTypeId: 유효 세트에 없으면 null (FK nullable)
            Integer contentTypeId = parseIntOrNull(item.getContenttypeid());
            if (contentTypeId != null && !validContentTypeIds.contains(contentTypeId)) {
                contentTypeId = null;
            }
            dto.setContentTypeId(contentTypeId);

            // sido/gugun FK 검증
            Integer sidoCode = parseIntOrNull(item.getLDongRegnCd());
            Integer gugunCode = parseIntOrNull(item.getLDongSignguCd());
            boolean fkCounted = false;
            if (sidoCode != null && gugunCode != null) {
                String key = sidoCode + "-" + gugunCode;
                if (!validGugunKeys.contains(key)) {
                    gugunCode = null;       // 복합 FK 위반 → gugun만 null
                    skippedFk++;
                    fkCounted = true;
                }
            }
            if (sidoCode != null && !validSidoCodes.contains(sidoCode)) {
                sidoCode = null;            // sido FK 위반 → 둘 다 null
                gugunCode = null;
                if (!fkCounted) {
                    skippedFk++;
                }
            }
            dto.setSidoCode(sidoCode);
            dto.setGugunCode(gugunCode);

            // 좌표: mapx=longitude, mapy=latitude. 비거나 0/NaN/bbox 밖이면 둘 다 null.
            Double longitude = parseDoubleOrNull(item.getMapx());
            Double latitude = parseDoubleOrNull(item.getMapy());
            if (!validCoords(latitude, longitude)) {
                latitude = null;
                longitude = null;
            }
            dto.setLatitude(latitude);
            dto.setLongitude(longitude);

            // mapLevel
            dto.setMapLevel(parseIntOrNull(item.getMlevel()));

            // 이미지: http로 시작할 때만
            dto.setFirstImage1(httpUrlOrNull(item.getFirstimage()));
            dto.setFirstImage2(httpUrlOrNull(item.getFirstimage2()));

            // tel/addr (tel은 VARCHAR(100) 초과 시 절단)
            dto.setTel(truncate(blankToNull(item.getTel()), TEL_MAX));
            dto.setAddr1(blankToNull(item.getAddr1()));
            dto.setAddr2(blankToNull(item.getAddr2()));

            // modifiedTime
            dto.setModifiedTime(parseModifiedTime(item.getModifiedtime()));

            // homepage/overview: list API에 없음 → null (upsert가 기존 값 보존)
            out.add(dto);
        }

        return new ConvertResult(out, skippedValidation, skippedFk);
    }

    private boolean validCoords(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return false;
        }
        if (latitude.isNaN() || longitude.isNaN()) {
            return false;
        }
        if (latitude == 0.0 || longitude == 0.0) {
            return false;
        }
        return latitude >= LAT_MIN && latitude <= LAT_MAX
                && longitude >= LON_MIN && longitude <= LON_MAX;
    }

    private Integer parseIntOrNull(String s) {
        if (isBlank(s)) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseDoubleOrNull(String s) {
        if (isBlank(s)) {
            return null;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDateTime parseModifiedTime(String s) {
        if (isBlank(s)) {
            return null;
        }
        try {
            return LocalDateTime.parse(s.trim(), MODIFIED_TIME_FMT);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String httpUrlOrNull(String s) {
        if (s != null && s.startsWith("http")) {
            return s;
        }
        return null;
    }

    private String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }

    /** VARCHAR 한도 초과 문자열을 잘라낸다(null 안전). 한 행의 긴 값이 트랜잭션 전체를 깨는 것 방지. */
    private String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
