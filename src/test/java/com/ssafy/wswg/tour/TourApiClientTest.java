package com.ssafy.wswg.tour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import com.ssafy.wswg.tour.TourApiException.TourApiErrorType;
import com.ssafy.wswg.tour.dto.AreaBasedItem;
import com.ssafy.wswg.tour.dto.AreaBasedPage;
import com.ssafy.wswg.tour.dto.LdongItem;

/**
 * TourApiClient 단위 테스트. 실제 네트워크 없이 MockRestServiceServer로 응답을 모킹한다.
 * RestClient.Builder에 mock 서버를 bind하고, 같은 빌더를 클라이언트에 주입한다.
 */
class TourApiClientTest {

    private static final String BASE_URL = "http://apis.data.go.kr/B551011/KorService2";
    private static final String SERVICE_KEY = "TESTKEY1234567890abcdef";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private TourApiClient client;
    private TourApiProperties properties;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        properties = new TourApiProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setServiceKey(SERVICE_KEY);
        properties.setMobileApp("wswg");
        properties.setNumOfRows(1000);
        // 테스트 속도를 위해 백오프를 1ms로.
        properties.setRetryBackoffMs(1);
        properties.setRetryMaxAttempts(3);

        client = new TourApiClient(builder, properties);
    }

    private String fixture(String name) {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource("fixtures/tour/" + name).getInputStream(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("fixture not found: " + name, e);
        }
    }

    @Test
    void fetchSidos_parsesRnumCodeName() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/ldongCode2")))
                .andRespond(withSuccess(fixture("sidos.json"), MediaType.APPLICATION_JSON));

        List<LdongItem> sidos = client.fetchSidos();

        assertThat(sidos).hasSize(3);
        assertThat(sidos.get(0).getRnum()).isEqualTo(1);
        assertThat(sidos.get(0).getCode()).isEqualTo("11");
        assertThat(sidos.get(0).getName()).isEqualTo("서울특별시");
        assertThat(sidos.get(2).getCode()).isEqualTo("44");
        server.verify();
    }

    @Test
    void fetchGuguns_parsesItems() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/ldongCode2")))
                .andExpect(queryParam("lDongRegnCd", "11"))
                .andRespond(withSuccess(fixture("guguns.json"), MediaType.APPLICATION_JSON));

        List<LdongItem> guguns = client.fetchGuguns(11);

        assertThat(guguns).hasSize(2);
        assertThat(guguns.get(0).getCode()).isEqualTo("110");
        assertThat(guguns.get(0).getName()).isEqualTo("종로구");
        server.verify();
    }

    @Test
    void fetchAreaPage_mapsKeyFieldsAndTotalCount() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/areaBasedList2")))
                .andExpect(queryParam("arrange", "C"))
                .andRespond(withSuccess(fixture("area-page.json"), MediaType.APPLICATION_JSON));

        AreaBasedPage page = client.fetchAreaPage(1, 1000);

        assertThat(page.getTotalCount()).isEqualTo(50710);
        assertThat(page.getPageNo()).isEqualTo(1);
        assertThat(page.getNumOfRows()).isEqualTo(1000);
        assertThat(page.getItems()).hasSize(2);

        AreaBasedItem first = page.getItems().get(0);
        assertThat(first.getContentid()).isEqualTo("3102220");
        assertThat(first.getMapx()).isEqualTo("126.3062683445");
        assertThat(first.getMapy()).isEqualTo("36.6392865898");
        assertThat(first.getModifiedtime()).isEqualTo("20260612191231");
        assertThat(first.getLDongRegnCd()).isEqualTo("44");
        assertThat(first.getLDongSignguCd()).isEqualTo("825");
        assertThat(first.getLclsSystm1()).isEqualTo("AC");

        AreaBasedItem second = page.getItems().get(1);
        assertThat(second.getContentid()).isEqualTo("126508");
        assertThat(second.getTitle()).isEqualTo("경복궁");
        server.verify();
    }

    @Test
    void fetchAreaPage_emptyItems_returnsEmptyListButReadsTotalCount() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/areaBasedList2")))
                .andRespond(withSuccess(fixture("area-empty.json"), MediaType.APPLICATION_JSON));

        AreaBasedPage page = client.fetchAreaPage(99, 1000);

        assertThat(page.getItems()).isEmpty();
        assertThat(page.getTotalCount()).isEqualTo(50710);
        assertThat(page.getPageNo()).isEqualTo(99);
        server.verify();
    }

    @Test
    void fetchAreaPage_singleItemObject_returnsOneElementList() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/areaBasedList2")))
                .andRespond(withSuccess(fixture("area-single.json"), MediaType.APPLICATION_JSON));

        AreaBasedPage page = client.fetchAreaPage(1, 1000);

        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getItems().get(0).getContentid()).isEqualTo("3102220");
        assertThat(page.getTotalCount()).isEqualTo(1);
        server.verify();
    }

    @Test
    void resultCode22_throwsQuota_notRetryable() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/areaBasedList2")))
                .andRespond(withSuccess(fixture("error-quota.json"), MediaType.APPLICATION_JSON));

        TourApiException ex = catchThrowableOfType(
                () -> client.fetchAreaPage(1, 1000), TourApiException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorType()).isEqualTo(TourApiErrorType.QUOTA);
        assertThat(ex.isRetryable()).isFalse();
        assertThat(ex.getResultCode()).isEqualTo("22");
        server.verify();
    }

    @Test
    void resultCode30_throwsKey_notRetryable() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/ldongCode2")))
                .andRespond(withSuccess(fixture("error-key.json"), MediaType.APPLICATION_JSON));

        TourApiException ex = catchThrowableOfType(
                client::fetchSidos, TourApiException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorType()).isEqualTo(TourApiErrorType.KEY);
        assertThat(ex.isRetryable()).isFalse();
        assertThat(ex.getResultCode()).isEqualTo("30");
        server.verify();
    }

    @Test
    void transient500_thenSuccess_succeedsAfterRetry() {
        // 첫 2회 HTTP 500, 3회차 정상 → 재시도로 성공.
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/areaBasedList2")))
                .andRespond(withServerError());
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/areaBasedList2")))
                .andRespond(withServerError());
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/areaBasedList2")))
                .andRespond(withSuccess(fixture("area-page.json"), MediaType.APPLICATION_JSON));

        AreaBasedPage page = client.fetchAreaPage(1, 1000);

        assertThat(page.getItems()).hasSize(2);
        assertThat(page.getTotalCount()).isEqualTo(50710);
        server.verify();
    }

    @Test
    void transientResultCode01_thenSuccess_succeedsAfterRetry() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/areaBasedList2")))
                .andRespond(withSuccess(fixture("error-transient.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/areaBasedList2")))
                .andRespond(withSuccess(fixture("area-page.json"), MediaType.APPLICATION_JSON));

        AreaBasedPage page = client.fetchAreaPage(1, 1000);

        assertThat(page.getItems()).hasSize(2);
        server.verify();
    }

    @Test
    void transient_allAttemptsFail_throws() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/areaBasedList2")))
                .andRespond(withServerError());
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/areaBasedList2")))
                .andRespond(withServerError());
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/areaBasedList2")))
                .andRespond(withServerError());

        TourApiException ex = catchThrowableOfType(
                () -> client.fetchAreaPage(1, 1000), TourApiException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorType()).isEqualTo(TourApiErrorType.TRANSIENT);
        assertThat(ex.isRetryable()).isTrue();
        server.verify();
    }

    @Test
    void serviceKey_presentAndSingleEncoded() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("serviceKey=" + SERVICE_KEY)))
                .andExpect(queryParam("serviceKey", SERVICE_KEY))
                .andExpect(queryParam("MobileOS", "ETC"))
                .andExpect(queryParam("MobileApp", "wswg"))
                .andExpect(queryParam("_type", "json"))
                .andRespond(withSuccess(fixture("sidos.json"), MediaType.APPLICATION_JSON));

        client.fetchSidos();

        server.verify();
    }

    @Test
    void unknownResultCode_throwsUnknown() {
        // 방어: 분류 불가 코드. 별도 픽스처 없이 인라인.
        String body = "{\"response\":{\"header\":{\"resultCode\":\"99\",\"resultMsg\":\"X\"},"
                + "\"body\":{\"items\":\"\",\"numOfRows\":0,\"pageNo\":1,\"totalCount\":0}}}";
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/ldongCode2")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(client::fetchSidos)
                .isInstanceOf(TourApiException.class)
                .extracting("errorType")
                .isEqualTo(TourApiErrorType.UNKNOWN);
        server.verify();
    }

    @Test
    void serviceKey_withSpecialChars_isSingleEncoded() {
        // data.go.kr Decoding 키에 흔한 +, /, = 가 정확히 1회 인코딩되는지(이중 인코딩 아님) 검증.
        RestClient.Builder b2 = RestClient.builder();
        MockRestServiceServer s2 = MockRestServiceServer.bindTo(b2).build();

        TourApiProperties p2 = new TourApiProperties();
        p2.setBaseUrl(BASE_URL);
        p2.setServiceKey("ab+cd/ef==");
        p2.setMobileApp("wswg");
        p2.setNumOfRows(1000);
        p2.setRetryBackoffMs(1);
        p2.setRetryMaxAttempts(3);

        TourApiClient c2 = new TourApiClient(b2, p2);

        // +→%2B, /→%2F, =→%3D 로 1회 인코딩. 이중 인코딩이면 %252B 가 되어 매칭 실패.
        s2.expect(requestTo(org.hamcrest.Matchers.containsString("serviceKey=ab%2Bcd%2Fef%3D%3D")))
                .andRespond(withSuccess(fixture("sidos.json"), MediaType.APPLICATION_JSON));

        c2.fetchSidos();
        s2.verify();
    }

    @Test
    void resultCode02_isTransientRetryable() {
        // 02=DB_ERROR 는 일시적 → 재시도 대상이어야 한다.
        TourApiException ex = TourApiException.fromResultCode("02", "DB_ERROR");
        assertThat(ex.getErrorType()).isEqualTo(TourApiErrorType.TRANSIENT);
        assertThat(ex.isRetryable()).isTrue();
    }
}
