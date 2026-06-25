package com.ssafy.wswg.external.route;

import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ssafy.wswg.model.dto.RouteLeg;
import com.ssafy.wswg.model.dto.TravelMode;

import lombok.extern.slf4j.Slf4j;

/**
 * 네이버 클라우드 플랫폼 Directions 5 (자동차 길찾기).
 *
 * <p>대중교통은 네이버 공개 API로 제공되지 않아 {@link OdsayTransitClient}를 쓴다. 이동시간은
 * 부가정보이므로 어떤 실패에도 예외를 던지지 않고 {@link RouteLeg#unavailable}을 돌려준다.
 */
@Slf4j
@Component
public class NaverDirectionsClient {
    private static final String PROVIDER = "naver-directions";

    private final RestClient restClient;
    private final String keyId;
    private final String key;

    public NaverDirectionsClient(
            RestClient.Builder restClientBuilder,
            @Value("${naver.directions.key-id:}") String keyId,
            @Value("${naver.directions.url:https://maps.apigw.ntruss.com/map-direction/v1/driving}") String url,
            @Value("${naver.directions.key:}") String key) {
        this.restClient = restClientBuilder.baseUrl(url).build();
        this.keyId = keyId;
        this.key = key;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(keyId) && StringUtils.hasText(key);
    }

    public RouteLeg drive(double fromLat, double fromLng, double toLat, double toLng) {
        if (!isConfigured()) {
            log.warn("네이버 길찾기 건너뜀: 자격증명 미설정 (NAVER_DIRECTIONS_KEY_ID/KEY)");
            return RouteLeg.unavailable(TravelMode.CAR, PROVIDER);
        }

        try {
            DirectionsResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("start", coord(fromLng, fromLat))
                            .queryParam("goal", coord(toLng, toLat))
                            .queryParam("option", "trafast")
                            .build())
                    .header("X-NCP-APIGW-API-KEY-ID", keyId)
                    .header("X-NCP-APIGW-API-KEY", key)
                    .retrieve()
                    .body(DirectionsResponse.class);

            Summary summary = response == null || response.route() == null || response.route().trafast() == null
                    || response.route().trafast().isEmpty()
                    ? null
                    : response.route().trafast().get(0).summary();
            if (response == null || response.code() != 0 || summary == null
                    || summary.distance() == null || summary.duration() == null) {
                log.warn("네이버 길찾기 실패: code={} message={}",
                        response == null ? null : response.code(),
                        response == null ? null : response.message());
                return RouteLeg.unavailable(TravelMode.CAR, PROVIDER);
            }

            // distance: meters, duration: milliseconds
            return RouteLeg.available(TravelMode.CAR, summary.distance(),
                    Math.round(summary.duration() / 1000.0), PROVIDER);
        } catch (Exception e) {
            log.warn("네이버 길찾기 호출 오류 ({})", e.getClass().getSimpleName(), e);
            return RouteLeg.unavailable(TravelMode.CAR, PROVIDER);
        }
    }

    private String coord(double lng, double lat) {
        return String.format(Locale.ROOT, "%f,%f", lng, lat);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DirectionsResponse(int code, String message, Route route) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Route(List<Path> trafast) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Path(Summary summary) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Summary(Long distance, Long duration) {
    }
}
