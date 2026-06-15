package com.ssafy.wswg;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;
import com.ssafy.wswg.model.service.TourLoadScheduler;
import com.ssafy.wswg.model.service.TourLoadService;

/**
 * TourLoadScheduler 단위 테스트: load() 위임 + 예외 흡수 검증.
 */
@ExtendWith(MockitoExtension.class)
class TourLoadSchedulerTest {

    @Mock
    private TourLoadService tourLoadService;

    @InjectMocks
    private TourLoadScheduler scheduler;

    @Test
    void runWeeklyLoad_callsLoad() {
        scheduler.runWeeklyLoad();

        verify(tourLoadService, times(1)).load();
    }

    @Test
    void runWeeklyLoad_swallowsExceptionFromLoad() {
        given(tourLoadService.load())
                .willThrow(new CommonException(ErrorCode.TOUR_LOAD_ALREADY_RUNNING));

        assertThatCode(() -> scheduler.runWeeklyLoad()).doesNotThrowAnyException();

        verify(tourLoadService, times(1)).load();
    }
}
