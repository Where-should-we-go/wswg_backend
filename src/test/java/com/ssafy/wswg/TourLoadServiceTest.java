package com.ssafy.wswg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;
import com.ssafy.wswg.model.dao.BatchRunLogDao;
import com.ssafy.wswg.model.dao.RegionDao;
import com.ssafy.wswg.model.dto.AttractionDto;
import com.ssafy.wswg.model.dto.BatchRunLog;
import com.ssafy.wswg.model.dto.GugunDto;
import com.ssafy.wswg.model.service.AttractionWriter;
import com.ssafy.wswg.model.service.RegionLoader;
import com.ssafy.wswg.model.service.RegionLoader.RegionLoadResult;
import com.ssafy.wswg.model.service.TourLoadService;
import com.ssafy.wswg.model.service.TourLoadService.Status;
import com.ssafy.wswg.model.service.TourLoadService.TourLoadResult;
import com.ssafy.wswg.tour.AttractionItemConverter;
import com.ssafy.wswg.tour.AttractionItemConverter.ConvertResult;
import com.ssafy.wswg.tour.TourApiClient;
import com.ssafy.wswg.tour.TourApiException;
import com.ssafy.wswg.tour.TourApiException.TourApiErrorType;
import com.ssafy.wswg.tour.TourApiProperties;
import com.ssafy.wswg.tour.dto.AreaBasedItem;
import com.ssafy.wswg.tour.dto.AreaBasedPage;

/**
 * TourLoadService 오케스트레이션 단위 테스트(Docker 불필요, 모든 의존성 Mockito 목킹).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TourLoadServiceTest {

    @Mock RegionLoader regionLoader;
    @Mock TourApiClient tourApiClient;
    @Mock RegionDao regionDao;
    @Mock AttractionItemConverter converter;
    @Mock AttractionWriter attractionWriter;
    @Mock BatchRunLogDao batchRunLogDao;

    private TourApiProperties props() {
        TourApiProperties p = new TourApiProperties();
        p.setNumOfRows(1000);
        p.setCoverageThreshold(0.98);
        p.setUpsertChunkSize(500);
        return p;
    }

    private TourLoadService service(TourApiProperties props) {
        return new TourLoadService(regionLoader, tourApiClient, regionDao,
                converter, attractionWriter, batchRunLogDao, props);
    }

    private void stubRegions() {
        when(regionLoader.load()).thenReturn(new RegionLoadResult(17, 250));
        when(regionDao.selectAllSidoCodes()).thenReturn(List.of(11, 26));
        when(regionDao.selectAllGuguns()).thenReturn(List.of(new GugunDto(11, 110, "종로구")));
    }

    private AreaBasedPage page(int totalCount, int count) {
        List<AreaBasedItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(new AreaBasedItem());
        }
        return new AreaBasedPage(items, totalCount, 1, 1000);
    }

    @Test
    @DisplayName("커버리지 게이트: fetch가 임계치 미만 → DEGRADED, write 호출 안 함, DEGRADED 로그 1행")
    void coverageGate_belowThreshold_degraded() {
        TourLoadService service = service(props());
        stubRegions();
        // totalCount=1000인데 10개만 fetch → 게이트 차단
        when(tourApiClient.fetchAreaPage(eq(1), anyInt())).thenReturn(page(1000, 10));

        TourLoadResult r = service.load();

        assertThat(r.status()).isEqualTo(Status.DEGRADED);
        verify(attractionWriter, never()).write(any(), anyInt());
        verify(converter, never()).convert(any(), any(), any(), any());

        ArgumentCaptor<BatchRunLog> logCaptor = ArgumentCaptor.forClass(BatchRunLog.class);
        verify(batchRunLogDao, times(1)).insertLog(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("DEGRADED");
        assertThat(service.isRunning()).isFalse();
        assertThat(service.getLast()).isSameAs(r);
    }

    @Test
    @DisplayName("쿼터 초과: QUOTA 예외 → CommonException(TOUR_API_QUOTA_EXCEEDED), write 안 함, ABORTED 로그 1행")
    void quotaExceeded_aborts() {
        TourLoadService service = service(props());
        stubRegions();
        when(tourApiClient.fetchAreaPage(eq(1), anyInt()))
                .thenThrow(new TourApiException(TourApiErrorType.QUOTA, false, "22", "quota"));

        assertThatThrownBy(service::load)
                .isInstanceOf(CommonException.class)
                .satisfies(e -> assertThat(((CommonException) e).getErrorCode())
                        .isEqualTo(ErrorCode.TOUR_API_QUOTA_EXCEEDED));

        verify(attractionWriter, never()).write(any(), anyInt());

        ArgumentCaptor<BatchRunLog> logCaptor = ArgumentCaptor.forClass(BatchRunLog.class);
        verify(batchRunLogDao, times(1)).insertLog(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("ABORTED");
        assertThat(logCaptor.getValue().getErrorCode()).isEqualTo("QUOTA");
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    @DisplayName("첫 페이지 fetch 실패(재시도 소진): totalCount 미상 → ABORTED, write 안 함, SUCCESS 위장 금지")
    void firstPageFetchFails_aborts_notSuccess() {
        TourLoadService service = service(props());
        stubRegions();
        // 첫 페이지가 재시도 소진(TRANSIENT)으로 실패 → totalCount를 못 받음.
        when(tourApiClient.fetchAreaPage(eq(1), anyInt()))
                .thenThrow(new TourApiException(TourApiErrorType.TRANSIENT, true, null, "exhausted"));

        assertThatThrownBy(service::load)
                .isInstanceOf(CommonException.class)
                .satisfies(e -> assertThat(((CommonException) e).getErrorCode())
                        .isEqualTo(ErrorCode.TOUR_LOAD_FAILED));

        // 빈 쓰기로 SUCCESS 위장하지 않는다: write/convert 호출 안 됨, ABORTED 로그 1행
        verify(attractionWriter, never()).write(any(), anyInt());
        verify(converter, never()).convert(any(), any(), any(), any());

        ArgumentCaptor<BatchRunLog> logCaptor = ArgumentCaptor.forClass(BatchRunLog.class);
        verify(batchRunLogDao, times(1)).insertLog(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("ABORTED");
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    @DisplayName("정상 경로: 충분히 fetch → converter+writer 호출, SUCCESS 결과 + SUCCESS 로그 1행")
    void happyPath_success() {
        TourApiProperties p = props();
        p.setNumOfRows(1000);
        TourLoadService service = service(p);
        stubRegions();
        // totalCount=1000, 첫 페이지 1000개 → 1페이지로 100% 커버
        when(tourApiClient.fetchAreaPage(eq(1), anyInt())).thenReturn(page(1000, 1000));

        List<AttractionDto> converted = List.of(new AttractionDto(), new AttractionDto());
        when(converter.convert(any(), any(), any(), any()))
                .thenReturn(new ConvertResult(converted, 3, 4));

        TourLoadResult r = service.load();

        assertThat(r.status()).isEqualTo(Status.SUCCESS);
        assertThat(r.attractionCount()).isEqualTo(2);
        assertThat(r.totalCount()).isEqualTo(1000);
        assertThat(r.sidoCount()).isEqualTo(17);
        assertThat(r.gugunCount()).isEqualTo(250);
        assertThat(r.skippedValidation()).isEqualTo(3);
        assertThat(r.skippedFk()).isEqualTo(4);

        // writer가 변환된 리스트로 호출됐는지 확인
        verify(attractionWriter, times(1)).write(eq(converted), eq(500));

        ArgumentCaptor<BatchRunLog> logCaptor = ArgumentCaptor.forClass(BatchRunLog.class);
        verify(batchRunLogDao, times(1)).insertLog(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    @DisplayName("동시 실행 가드: 첫 호출이 진행 중이면 둘째 호출은 TOUR_LOAD_ALREADY_RUNNING")
    void concurrencyGuard_secondCallRejected() throws Exception {
        TourLoadService service = service(props());

        // regionLoader.load()를 래치로 막아 첫 load()를 진행 중 상태로 고정한다.
        CountDownLatch inLoad = new CountDownLatch(1);     // 첫 호출이 임계 영역 진입 신호
        CountDownLatch release = new CountDownLatch(1);    // 첫 호출 해제 신호
        when(regionLoader.load()).thenAnswer(inv -> {
            inLoad.countDown();
            release.await(5, TimeUnit.SECONDS);
            return new RegionLoadResult(0, 0);
        });
        // 첫 호출이 게이트로 빠르게 끝나도록(릴리즈 후) - fetch는 빈 결과
        when(regionDao.selectAllSidoCodes()).thenReturn(List.of());
        when(regionDao.selectAllGuguns()).thenReturn(List.of());
        when(tourApiClient.fetchAreaPage(anyInt(), anyInt())).thenReturn(page(0, 0));
        when(converter.convert(any(), any(), any(), any()))
                .thenReturn(new ConvertResult(List.of(), 0, 0));

        ExecutorService exec = Executors.newSingleThreadExecutor();
        Future<TourLoadResult> first = exec.submit(service::load);
        try {
            // 첫 호출이 임계 영역에 진입할 때까지 대기 → running=true 확정
            assertThat(inLoad.await(5, TimeUnit.SECONDS)).isTrue();

            // 둘째 호출(이 스레드)은 즉시 거부돼야 한다
            AtomicReference<Throwable> thrown = new AtomicReference<>();
            try {
                service.load();
            } catch (Throwable t) {
                thrown.set(t);
            }
            assertThat(thrown.get()).isInstanceOf(CommonException.class);
            assertThat(((CommonException) thrown.get()).getErrorCode())
                    .isEqualTo(ErrorCode.TOUR_LOAD_ALREADY_RUNNING);
        } finally {
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            exec.shutdownNow();
        }

        // 첫 호출 종료 후 가드 해제 확인
        assertThat(service.isRunning()).isFalse();
    }
}
