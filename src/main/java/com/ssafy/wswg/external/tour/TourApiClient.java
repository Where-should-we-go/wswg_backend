package com.ssafy.wswg.external.tour;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.wswg.external.tour.TourApiException.TourApiErrorType;
import com.ssafy.wswg.external.tour.dto.AreaBasedItem;
import com.ssafy.wswg.external.tour.dto.AreaBasedPage;
import com.ssafy.wswg.external.tour.dto.DetailCommonItem;
import com.ssafy.wswg.external.tour.dto.LdongItem;
import com.ssafy.wswg.external.tour.dto.TourApiResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * data.go.kr KorService2(TourAPI) 호출 클라이언트.
 *
 * <p>설계 메모:
 * <ul>
 *   <li>응답을 String으로 받아 내부 {@link #OBJECT_MAPPER}로 직접 파싱한다.
 *       KorService2의 빈 결과 {@code "items":""} 트랩을 ACCEPT_EMPTY_STRING_AS_NULL_OBJECT로
 *       흡수해야 하므로, 메시지 컨버터에 위임하지 않고 전용 ObjectMapper를 명시적으로 쓴다.</li>
 *   <li>serviceKey는 UriComponentsBuilder로 한 번만 인코딩한다(이중 인코딩 방지).</li>
 *   <li>재시도 가능한 실패(TRANSIENT / IOException / timeout / 5xx)는
 *       지수 백오프로 최대 {@code retryMaxAttempts}회 재시도한다. 재시도 불가
 *       오류는 즉시 전파한다.</li>
 * </ul>
 */
@Slf4j
public class TourApiClient {

    private static final String OK_CODE = "0000";

    /**
     * KorService2 응답 파싱 전용 ObjectMapper. 클라이언트 내부 구현이라 여기 둔다.
     * <b>스프링 빈으로 노출하지 않는다</b> — 네이키드 {@code ObjectMapper} 빈은 Spring Boot가
     * MVC 응답 직렬화에까지 써버려(JavaTimeModule 등 Boot 기본 설정 상실) {@code LocalDateTime}
     * 직렬화가 깨진다. 파싱 트랩(빈 문자열 items, 단건 item, 미지 필드)만 흡수한다.
     * ObjectMapper는 설정 후 thread-safe하므로 static 단일 인스턴스를 재사용한다.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final RestClient restClient;
    private final TourApiProperties properties;

    /**
     * @param builder RestClient 빌더. 타임아웃 등 요청 팩토리 설정은
     *                {@link TourApiConfig}에서 빌더에 적용해 주입한다(테스트는
     *                MockRestServiceServer에 바인딩된 빌더를 그대로 주입).
     *                클라이언트는 요청 팩토리를 덮어쓰지 않는다(목 서버를 보존하기 위함).
     */
    public TourApiClient(RestClient.Builder builder, TourApiProperties properties) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    /** 시도 목록(지역 파라미터 없음). */
    public List<LdongItem> fetchSidos() {
        URI uri = ldongUri(null);
        TourApiResponse<LdongItem> response = withRetry(() -> getAndParse(uri,
                new TypeReference<TourApiResponse<LdongItem>>() {}));
        return response.itemList();
    }

    /** 특정 시도의 시군구 목록. */
    public List<LdongItem> fetchGuguns(int regnCd) {
        URI uri = ldongUri(regnCd);
        TourApiResponse<LdongItem> response = withRetry(() -> getAndParse(uri,
                new TypeReference<TourApiResponse<LdongItem>>() {}));
        return response.itemList();
    }

    /** 관광지 페이지 조회. */
    public AreaBasedPage fetchAreaPage(int pageNo, int numOfRows) {
        URI uri = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path("/areaBasedList2")
                .queryParam("serviceKey", encodedServiceKey())
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", properties.getMobileApp())
                .queryParam("_type", "json")
                .queryParam("arrange", "C")
                .queryParam("numOfRows", numOfRows)
                .queryParam("pageNo", pageNo)
                .build(true)
                .toUri();

        TourApiResponse<AreaBasedItem> response = withRetry(() -> getAndParse(uri,
                new TypeReference<TourApiResponse<AreaBasedItem>>() {}));

        return new AreaBasedPage(
                response.itemList(),
                response.totalCount(),
                response.pageNo(),
                response.numOfRows());
    }

    /**
     * 관광지 공통 상세(detailCommon2). 개요/홈페이지 등 타입별 공통정보를 조회한다(A-6 write-through).
     * v4.4부터 요청은 contentId만 필요(YN 플래그 폐지). 단건 item을 반환하며, 결과가 없으면 null.
     */
    public DetailCommonItem fetchDetailCommon(int contentId) {
        URI uri = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path("/detailCommon2")
                .queryParam("serviceKey", encodedServiceKey())
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", properties.getMobileApp())
                .queryParam("_type", "json")
                .queryParam("contentId", contentId)
                .build(true)
                .toUri();

        TourApiResponse<DetailCommonItem> response = withRetry(() -> getAndParse(uri,
                new TypeReference<TourApiResponse<DetailCommonItem>>() {}));

        List<DetailCommonItem> items = response.itemList();
        return items.isEmpty() ? null : items.get(0);
    }

    private URI ldongUri(Integer regnCd) {
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path("/ldongCode2")
                .queryParam("serviceKey", encodedServiceKey())
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", properties.getMobileApp())
                .queryParam("_type", "json")
                .queryParam("numOfRows", properties.getNumOfRows())
                .queryParam("pageNo", 1);
        if (regnCd != null) {
            b.queryParam("lDongRegnCd", regnCd);
        }
        // build(true): 쿼리값을 이미 인코딩된 것으로 보고 이중 인코딩하지 않는다.
        // serviceKey는 encodedServiceKey()로 미리 정확히 1회 인코딩하고, 나머지 값은
        // 안전한 ASCII(ETC/wswg/json/숫자)뿐이라 그대로 둔다.
        return b.build(true).toUri();
    }

    /**
     * serviceKey를 정확히 1회 URL 인코딩한다.
     *
     * <p>application.yml의 {@code tour-api.service-key}는 data.go.kr의 <b>Decoding 키</b>(원문)를
     * 받는다. base64 키에 흔한 {@code + / =} 는 인코딩하지 않으면 쿼리에서 {@code +}가 공백으로
     * 해석되는 등 인증 실패를 유발하므로 여기서 한 번 인코딩한다. {@link URLEncoder}는
     * {@code +→%2B, /→%2F, =→%3D}로 변환하며(키에 공백은 없음), 호출부의 {@code build(true)}가
     * 재인코딩을 막아 이중 인코딩을 방지한다.
     */
    private String encodedServiceKey() {
        return URLEncoder.encode(properties.getServiceKey(), StandardCharsets.UTF_8);
    }

    /**
     * GET 호출 + 파싱 + resultCode 검증. 재시도 단위 1회를 수행한다.
     * 재시도 판단은 {@link #withRetry(Supplier)}가 던져진 예외로 한다.
     */
    private <T> TourApiResponse<T> getAndParse(URI uri, TypeReference<TourApiResponse<T>> type) {
        String body;
        try {
            body = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            // HTTP 5xx는 일시적 오류로 간주해 재시도, 그 외(4xx)는 재시도 불가.
            boolean retryable = e.getStatusCode().is5xxServerError();
            throw new TourApiException(
                    retryable ? TourApiErrorType.TRANSIENT : TourApiErrorType.UNKNOWN,
                    retryable, null,
                    "TourAPI HTTP error: " + e.getStatusCode(), e);
        } catch (Exception e) {
            // IOException/timeout 등 전송 계층 오류(ResourceAccessException 등) → 재시도.
            throw new TourApiException(TourApiErrorType.TRANSIENT, true, null,
                    "TourAPI transport error: " + e.getMessage(), e);
        }

        TourApiResponse<T> response;
        try {
            response = OBJECT_MAPPER.readValue(body, OBJECT_MAPPER.getTypeFactory()
                    .constructType(type));
        } catch (IOException e) {
            throw new TourApiException(TourApiErrorType.UNKNOWN, false, null,
                    "TourAPI response parse error", e);
        }

        String code = response.resultCode();
        if (!OK_CODE.equals(code)) {
            throw TourApiException.fromResultCode(code, response.resultMsg());
        }
        return response;
    }

    /**
     * 재시도 가능한 실패에 한해 지수 백오프로 재시도한다.
     * 백오프 base는 {@link TourApiProperties#getRetryBackoffMs()}이며 시도마다 ×2.
     */
    private <T> T withRetry(Supplier<T> call) {
        int maxAttempts = Math.max(1, properties.getRetryMaxAttempts());
        long backoff = properties.getRetryBackoffMs();
        TourApiException last = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call.get();
            } catch (TourApiException e) {
                last = e;
                if (!e.isRetryable() || attempt == maxAttempts) {
                    throw e;
                }
                log.warn("TourAPI call failed (attempt {}/{}, type={}): {}. retrying...",
                        attempt, maxAttempts, e.getErrorType(), e.getMessage());
                sleep(backoff);
                backoff *= 2;
            }
        }
        // 도달 불가(루프 내에서 항상 return 또는 throw). 방어적 처리.
        throw last;
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new TourApiException(TourApiErrorType.UNKNOWN, false, null,
                    "TourAPI retry interrupted", ie);
        }
    }
}
