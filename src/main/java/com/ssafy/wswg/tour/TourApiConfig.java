package com.ssafy.wswg.tour;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * TourApiClient용 인프라 빈.
 * <ul>
 *   <li>{@link #tourApiObjectMapper()} - KorService2 응답의 파싱 트랩
 *       (빈 문자열 items, 단건 item 객체, 미지 필드)을 흡수하도록 설정한 ObjectMapper.</li>
 *   <li>{@link #tourApiRestClientBuilder(TourApiProperties)} - connect/read 타임아웃을
 *       적용한 요청 팩토리를 가진 빌더. 클라이언트는 이 빌더의 요청 팩토리를
 *       덮어쓰지 않으므로, 테스트는 이 빌더 대신 MockRestServiceServer에
 *       바인딩된 빌더를 주입하면 목 서버가 그대로 동작한다.</li>
 * </ul>
 */
@Configuration
public class TourApiConfig {

    @Bean
    public RestClient.Builder tourApiRestClientBuilder(TourApiProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        return RestClient.builder().requestFactory(factory);
    }

    @Bean
    public TourApiClient tourApiClient(RestClient.Builder tourApiRestClientBuilder,
            TourApiProperties properties) {
        return new TourApiClient(tourApiRestClientBuilder, properties, tourApiObjectMapper());
    }

    /**
     * TourAPI 응답 전용 ObjectMapper. <b>일부러 빈으로 등록하지 않는다.</b>
     * 네이키드 {@code ObjectMapper} 빈을 노출하면 Spring Boot가 MVC 응답 직렬화에도
     * 이 매퍼를 써버려(JavaTimeModule 등 Boot 기본 설정 상실) {@code LocalDateTime}
     * 직렬화가 깨진다. 그래서 클라이언트 생성 시점에만 만들어 주입한다.
     */
    private ObjectMapper tourApiObjectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
