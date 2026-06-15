package com.ssafy.wswg.external.tour;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * data.go.kr KorService2 (TourAPI) 호출에 필요한 설정값.
 * application.yml의 {@code tour-api:} 블록에 바인딩된다.
 */
@Getter
@Setter
@ToString(exclude = "serviceKey")
@ConfigurationProperties("tour-api")
public class TourApiProperties {

    /** API base URL. 예) http://apis.data.go.kr/B551011/KorService2 */
    private String baseUrl;

    /** data.go.kr 인증키. 모든 호출의 serviceKey 쿼리 파라미터로 사용된다. */
    private String serviceKey;

    /** MobileApp 공통 파라미터 값. */
    private String mobileApp = "wswg";

    /** 한 페이지당 조회 건수 기본값. */
    private int numOfRows = 1000;

    /** 커넥션 타임아웃(ms). */
    private int connectTimeoutMs = 5000;

    /** 응답 읽기 타임아웃(ms). */
    private int readTimeoutMs = 10000;

    /** 재시도 가능한 실패 시 최대 시도 횟수(최초 시도 포함). */
    private int retryMaxAttempts = 3;

    /** 재시도 백오프 기준 시간(ms). 시도마다 ×2로 증가한다. */
    private long retryBackoffMs = 1000;

    /** 쓰기를 허용하기 위해 fetch해야 하는 totalCount 대비 최소 비율(커버리지 게이트). */
    private double coverageThreshold = 0.98;

    /** attractions upsert를 끊어서 보낼 청크 크기. */
    private int upsertChunkSize = 500;

    /** 주간 자동 적재 스케줄(cron). 기본값: 매주 월요일 04:00. */
    private String scheduleCron = "0 0 4 * * MON";
}
