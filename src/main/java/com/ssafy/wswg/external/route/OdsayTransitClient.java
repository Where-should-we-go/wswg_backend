package com.ssafy.wswg.external.route;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ssafy.wswg.model.dto.RouteLeg;
import com.ssafy.wswg.model.dto.TravelMode;

import lombok.extern.slf4j.Slf4j;

/**
 * ODsay 대중교통 길찾기 (searchPubTransPath).
 *
 * <p>네이버 공개 API에 대중교통 길찾기가 없어 대중교통 모드는 ODsay를 쓴다. 이동시간은
 * 부가정보이므로 실패해도 예외를 던지지 않고 {@link RouteLeg#unavailable}을 돌려준다.
 * apiKey는 RestClient의 URI 빌더가 자동 인코딩하므로 디코딩(원본) 키를 주입한다.
 */
@Slf4j
@Component
public class OdsayTransitClient {
    private static final String PROVIDER = "odsay";

    private final RestClient restClient;
    private final String apiKey;
    private final String referer;

    public OdsayTransitClient(
            RestClient.Builder restClientBuilder,
            @Value("${odsay.url:https://api.odsay.com/v1/api/searchPubTransPath}") String url,
            @Value("${odsay.api-key:}") String apiKey,
            @Value("${odsay.referer:http://localhost}") String referer) {
        this.restClient = restClientBuilder.baseUrl(url).build();
        this.apiKey = apiKey;
        // ODsay 웹 서비스 키는 등록된 URI(도메인)와 일치하는 Referer로 인증한다.
        this.referer = referer;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey);
    }

    public RouteLeg transit(double fromLat, double fromLng, double toLat, double toLng) {
        if (!isConfigured()) {
            log.warn("ODsay 대중교통 건너뜀: 자격증명 미설정 (ODSAY_API_KEY)");
            return RouteLeg.unavailable(TravelMode.TRANSIT, PROVIDER);
        }

        try {
            OdsayResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("SX", fromLng)
                            .queryParam("SY", fromLat)
                            .queryParam("EX", toLng)
                            .queryParam("EY", toLat)
                            .queryParam("apiKey", apiKey)
                            .build())
                    .header("Referer", referer)
                    .retrieve()
                    .body(OdsayResponse.class);

            if (response != null && response.error() != null && !response.error().isEmpty()) {
                OdsayError error = response.error().get(0);
                log.warn("ODsay 대중교통 실패: code={} message={}", error.code(), error.message());
                return RouteLeg.unavailable(TravelMode.TRANSIT, PROVIDER);
            }

            Info info = response == null || response.result() == null || response.result().path() == null
                    || response.result().path().isEmpty()
                    ? null
                    : response.result().path().get(0).info();
            if (info == null || info.totalTime() == null || info.totalDistance() == null) {
                log.warn("ODsay 대중교통 실패: 경로 정보 없음");
                return RouteLeg.unavailable(TravelMode.TRANSIT, PROVIDER);
            }

            // totalTime: minutes, totalDistance: meters
            return RouteLeg.available(TravelMode.TRANSIT, info.totalDistance(),
                    info.totalTime() * 60L, PROVIDER);
        } catch (Exception e) {
            log.warn("ODsay 대중교통 호출 오류 ({})", e.getClass().getSimpleName(), e);
            return RouteLeg.unavailable(TravelMode.TRANSIT, PROVIDER);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OdsayResponse(Result result, List<OdsayError> error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OdsayError(String code, String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Result(List<Path> path) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Path(Info info) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Info(Integer totalTime, Integer totalDistance, Integer payment) {
    }
}
