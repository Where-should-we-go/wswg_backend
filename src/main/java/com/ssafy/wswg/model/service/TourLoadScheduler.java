package com.ssafy.wswg.model.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A-3 TourAPI 주간 자동 적재 스케줄러.
 *
 * <p>{@code tour-api.schedule-cron}(기본: 매주 월 04:00) 주기로 {@link TourLoadService#load()}를
 * 호출한다. 적재 결과(이미 실행 중/쿼터/실패 등)는 엔진이 batch_run_log에 기록하므로,
 * 여기서는 예외를 밖으로 전파하지 않고 로그만 남긴다(스케줄러 스레드 종료 방지).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TourLoadScheduler {

    private final TourLoadService tourLoadService;

    // 플레이스홀더 자체에 기본값을 둔다. @ConfigurationProperties의 필드 기본값은
    // @Scheduled 어노테이션 해석에 적용되지 않으므로, yml에 키가 없어도 기동되도록 한다.
    @Scheduled(cron = "${tour-api.schedule-cron:0 0 4 * * MON}")
    public void runWeeklyLoad() {
        log.info("Scheduled tour load triggered");
        try {
            TourLoadService.TourLoadResult result = tourLoadService.load();
            log.info("Scheduled tour load finished: status={}, attractions={}",
                    result.status(), result.attractionCount());
        } catch (Exception e) {
            // 이미 실행 중/쿼터 초과/적재 실패 등은 엔진이 로그에 기록했으므로 여기선 삼킨다.
            log.warn("Scheduled tour load skipped/failed: {}", e.getMessage());
        }
    }
}
