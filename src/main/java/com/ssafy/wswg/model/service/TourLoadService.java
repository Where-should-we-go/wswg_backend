package com.ssafy.wswg.model.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;
import com.ssafy.wswg.model.dao.BatchRunLogDao;
import com.ssafy.wswg.model.dao.RegionDao;
import com.ssafy.wswg.model.dto.BatchRunLog;
import com.ssafy.wswg.model.dto.GugunDto;
import com.ssafy.wswg.model.service.RegionLoader.RegionLoadResult;
import com.ssafy.wswg.external.tour.AttractionItemConverter;
import com.ssafy.wswg.external.tour.AttractionItemConverter.ConvertResult;
import com.ssafy.wswg.external.tour.TourApiClient;
import com.ssafy.wswg.external.tour.TourApiException;
import com.ssafy.wswg.external.tour.TourApiException.TourApiErrorType;
import com.ssafy.wswg.external.tour.TourApiProperties;
import com.ssafy.wswg.external.tour.dto.AreaBasedItem;
import com.ssafy.wswg.external.tour.dto.AreaBasedPage;

import lombok.extern.slf4j.Slf4j;

/**
 * A-3 TourAPI 적재 오케스트레이터.
 *
 * <p><b>fetch-바깥 / write-안:</b> 지역 적재({@link RegionLoader})와 모든 관광지 페이지
 * HTTP fetch를 트랜잭션 <b>바깥</b>에서 끝내 메모리에 모은 뒤, 변환·검증을 거쳐
 * 마지막에 {@link AttractionWriter#write}로 단 한 번만 트랜잭션 <b>안</b>에서 쓴다.
 * 그래서 이 빈 자체에는 {@code @Transactional}을 두지 않는다(HTTP가 트랜잭션을 점유 X).
 *
 * <p><b>보호 장치:</b>
 * <ul>
 *   <li>동시 실행 가드({@link AtomicBoolean}): 두 번째 동시 호출은 즉시 거부.</li>
 *   <li>커버리지 게이트: fetch가 totalCount의 임계치 미만이면 쓰지 않고 DEGRADED.</li>
 *   <li>QUOTA/KEY 오류는 ABORTED로 즉시 중단(쓰지 않음 → last-good 보존).</li>
 * </ul>
 *
 * <p><b>로그 불변식:</b> 모든 종료 경로가 정확히 1개의 batch_run_log 행을 남긴다.
 */
@Slf4j
@Service
public class TourLoadService {

    public static final String JOB_NAME = "tour-load";

    /** 검증 통과 contentType(seed 8종). */
    private static final Set<Integer> VALID_CONTENT_TYPE_IDS =
            Set.of(12, 14, 15, 25, 28, 32, 38, 39);

    private final RegionLoader regionLoader;
    private final TourApiClient tourApiClient;
    private final RegionDao regionDao;
    private final AttractionItemConverter converter;
    private final AttractionWriter attractionWriter;
    private final BatchRunLogDao batchRunLogDao;
    private final TourApiProperties properties;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile TourLoadResult last;

    public TourLoadService(RegionLoader regionLoader,
            TourApiClient tourApiClient,
            RegionDao regionDao,
            AttractionItemConverter converter,
            AttractionWriter attractionWriter,
            BatchRunLogDao batchRunLogDao,
            TourApiProperties properties) {
        this.regionLoader = regionLoader;
        this.tourApiClient = tourApiClient;
        this.regionDao = regionDao;
        this.converter = converter;
        this.attractionWriter = attractionWriter;
        this.batchRunLogDao = batchRunLogDao;
        this.properties = properties;
    }

    public boolean isRunning() {
        return running.get();
    }

    public TourLoadResult getLast() {
        return last;
    }

    /** 적재 결과 요약. */
    public enum Status {SUCCESS, DEGRADED, ABORTED}

    public record TourLoadResult(
            Status status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            int totalCount,
            int attractionCount,
            int sidoCount,
            int gugunCount,
            int skippedValidation,
            int skippedFk,
            String errorCode,
            String errorMessage) {
    }

    /**
     * 지역 + 관광지를 적재한다. 동시 실행 1개만 허용한다.
     */
    public TourLoadResult load() {
        // 1. 동시 실행 가드
        if (!running.compareAndSet(false, true)) {
            throw new CommonException(ErrorCode.TOUR_LOAD_ALREADY_RUNNING);
        }
        LocalDateTime startedAt = LocalDateTime.now();
        try {
            // a. 지역 적재(자체 fetch + 트랜잭션)
            RegionLoadResult region = regionLoader.load();

            // b. FK 검증 세트 구성
            Set<Integer> validSidoCodes = new HashSet<>(regionDao.selectAllSidoCodes());
            Set<String> validGugunKeys = regionDao.selectAllGuguns().stream()
                    .map(g -> g.getSidoCode() + "-" + g.getGugunCode())
                    .collect(Collectors.toSet());

            // c. 모든 관광지 페이지 fetch (HTTP, 트랜잭션 밖)
            int numOfRows = properties.getNumOfRows();
            List<AreaBasedItem> accumulated = new ArrayList<>();
            int totalCount = -1; // -1 = 첫 페이지 미수신(totalCount 미상)
            try {
                AreaBasedPage page1 = tourApiClient.fetchAreaPage(1, numOfRows);
                totalCount = page1.getTotalCount();
                addItems(accumulated, page1);

                int totalPages = numOfRows > 0 && totalCount > 0
                        ? (int) Math.ceil((double) totalCount / numOfRows) : 1;
                for (int pageNo = 2; pageNo <= totalPages; pageNo++) {
                    AreaBasedPage page = tourApiClient.fetchAreaPage(pageNo, numOfRows);
                    addItems(accumulated, page);
                }
            } catch (TourApiException e) {
                // QUOTA/KEY → 즉시 ABORT(쓰지 않음 → last-good 보존)
                if (e.getErrorType() == TourApiErrorType.QUOTA
                        || e.getErrorType() == TourApiErrorType.KEY) {
                    ErrorCode ec = e.getErrorType() == TourApiErrorType.QUOTA
                            ? ErrorCode.TOUR_API_QUOTA_EXCEEDED
                            : ErrorCode.TOUR_API_KEY_INVALID;
                    TourLoadResult aborted = new TourLoadResult(Status.ABORTED, startedAt,
                            LocalDateTime.now(), 0, 0,
                            region.sidoCount(), region.gugunCount(), 0, 0,
                            e.getErrorType().name(), e.getMessage());
                    recordLog(aborted);
                    last = aborted;
                    throw new CommonException(ec);
                }
                // 재시도 소진/UNKNOWN → 불완전 fetch.
                log.warn("TourAPI page fetch incomplete (type={}): {}",
                        e.getErrorType(), e.getMessage());
                if (totalCount < 0) {
                    // 첫 페이지조차 실패 → totalCount 미상 → 적재 불가 → ABORT.
                    // (빈 쓰기로 SUCCESS 위장 금지: fetch 전체 실패를 성공으로 보고하면 안 된다.)
                    TourLoadResult aborted = new TourLoadResult(Status.ABORTED, startedAt,
                            LocalDateTime.now(), 0, 0,
                            region.sidoCount(), region.gugunCount(), 0, 0,
                            ErrorCode.TOUR_LOAD_FAILED.name(),
                            "first page fetch failed: " + e.getMessage());
                    recordLog(aborted);
                    last = aborted;
                    throw new CommonException(ErrorCode.TOUR_LOAD_FAILED);
                }
                // 이후 페이지 실패: 실제 totalCount를 그대로 유지한 채 커버리지 게이트로 넘긴다.
                // (totalCount를 fetched로 덮으면 게이트가 항상 통과돼 부분 적재를 SUCCESS로 오인한다.)
            }

            // d. 커버리지 게이트
            int fetched = accumulated.size();
            if (totalCount > 0 && fetched < totalCount * properties.getCoverageThreshold()) {
                TourLoadResult degraded = new TourLoadResult(Status.DEGRADED, startedAt,
                        LocalDateTime.now(), totalCount, 0,
                        region.sidoCount(), region.gugunCount(), 0, 0,
                        null, "coverage below threshold: " + fetched + "/" + totalCount);
                recordLog(degraded);
                last = degraded;
                return degraded;   // 보호적 스킵: 예외 없이 DEGRADED 반환
            }

            // e. 변환 + 검증
            ConvertResult cr = converter.convert(accumulated, validSidoCodes,
                    validGugunKeys, VALID_CONTENT_TYPE_IDS);

            // f. 쓰기 (유일한 attraction 트랜잭션)
            attractionWriter.write(cr.attractions(), properties.getUpsertChunkSize());

            // g. SUCCESS
            TourLoadResult success = new TourLoadResult(Status.SUCCESS, startedAt,
                    LocalDateTime.now(), totalCount, cr.attractions().size(),
                    region.sidoCount(), region.gugunCount(),
                    cr.skippedValidation(), cr.skippedFk(), null, null);
            recordLog(success);
            last = success;
            return success;

        } catch (CommonException e) {
            // QUOTA/KEY 등 위에서 이미 로그 기록·last 세팅 후 던진 것 → 그대로 재전파.
            throw e;
        } catch (Exception e) {
            // 예기치 못한 오류 → ABORTED
            log.error("Tour load failed unexpectedly", e);
            TourLoadResult aborted = new TourLoadResult(Status.ABORTED, startedAt,
                    LocalDateTime.now(), 0, 0, 0, 0, 0, 0,
                    ErrorCode.TOUR_LOAD_FAILED.name(), e.getMessage());
            recordLog(aborted);
            last = aborted;
            throw new CommonException(ErrorCode.TOUR_LOAD_FAILED);
        } finally {
            running.set(false);
        }
    }

    private void addItems(List<AreaBasedItem> acc, AreaBasedPage page) {
        List<AreaBasedItem> items = page.getItems();
        if (items != null) {
            acc.addAll(items);
        }
    }

    private void recordLog(TourLoadResult r) {
        BatchRunLog log = BatchRunLog.builder()
                .jobName(JOB_NAME)
                .status(r.status().name())
                .startedAt(r.startedAt())
                .finishedAt(r.finishedAt())
                .totalCount(r.totalCount())
                .attractionCount(r.attractionCount())
                .sidoCount(r.sidoCount())
                .gugunCount(r.gugunCount())
                .skippedValidation(r.skippedValidation())
                .skippedFk(r.skippedFk())
                .errorCode(r.errorCode())
                .errorMessage(r.errorMessage())
                .build();
        batchRunLogDao.insertLog(log);
    }
}
