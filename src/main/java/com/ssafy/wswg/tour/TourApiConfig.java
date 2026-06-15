package com.ssafy.wswg.tour;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * TourApiClient용 인프라 빈.
 *
 * <p>{@link #tourApiRestClientBuilder(TourApiProperties)} - connect/read 타임아웃을 적용한
 * 요청 팩토리를 가진 빌더. 클라이언트는 이 빌더의 요청 팩토리를 덮어쓰지 않으므로,
 * 테스트는 이 빌더 대신 MockRestServiceServer에 바인딩된 빌더를 주입하면 목 서버가
 * 그대로 동작한다.
 *
 * <p>응답 파싱용 ObjectMapper는 {@link TourApiClient} 내부 구현이라 여기서 다루지 않는다
 * (네이키드 ObjectMapper 빈을 노출하면 MVC 응답 직렬화까지 침범하므로).
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
        return new TourApiClient(tourApiRestClientBuilder, properties);
    }
}
