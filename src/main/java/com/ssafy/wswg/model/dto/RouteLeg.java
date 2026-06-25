package com.ssafy.wswg.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 두 지점 사이의 이동 구간 정보.
 *
 * <p>이동 시간은 부가정보라 길찾기 API가 실패해도 여행 생성을 막지 않는다. 실패 시
 * {@code available=false} + 거리/시간 null로 표현한다(graceful degradation). Redis 캐시에
 * JSON으로 직렬화되므로 기본 생성자와 게터/세터를 둔다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RouteLeg {
    private TravelMode mode;
    private Long distanceMeters;
    private Long durationSeconds;
    private String provider;
    private boolean available;

    public RouteLeg() {
    }

    private RouteLeg(TravelMode mode, Long distanceMeters, Long durationSeconds, String provider, boolean available) {
        this.mode = mode;
        this.distanceMeters = distanceMeters;
        this.durationSeconds = durationSeconds;
        this.provider = provider;
        this.available = available;
    }

    public static RouteLeg available(TravelMode mode, long distanceMeters, long durationSeconds, String provider) {
        return new RouteLeg(mode, distanceMeters, durationSeconds, provider, true);
    }

    public static RouteLeg unavailable(TravelMode mode, String provider) {
        return new RouteLeg(mode, null, null, provider, false);
    }

    public TravelMode getMode() {
        return mode;
    }

    public void setMode(TravelMode mode) {
        this.mode = mode;
    }

    public Long getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(Long distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
}
