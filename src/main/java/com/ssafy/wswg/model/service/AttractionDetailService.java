package com.ssafy.wswg.model.service;

import org.springframework.stereotype.Service;

import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;
import com.ssafy.wswg.external.tour.TourApiClient;
import com.ssafy.wswg.external.tour.TourApiException;
import com.ssafy.wswg.external.tour.dto.DetailCommonItem;
import com.ssafy.wswg.external.tour.dto.DetailIntroItem;
import com.ssafy.wswg.model.dao.AttractionDao;
import com.ssafy.wswg.model.dto.AttractionDetailDto;

import lombok.RequiredArgsConstructor;

/**
 * 관광지 상세(S4 · A-6). DB 단건 조회 후, 비어 있는 overview/홈페이지와 휴무일을
 * TourAPI write-through로 lazy fill 한다.
 *
 * <p><b>트랜잭션 경계:</b> 메서드에 {@code @Transactional}을 걸지 <b>않는다</b>. 외부 HTTP
 * (detailCommon2/detailIntro2)를 트랜잭션 안에서 기다리면 느린 호출이 DB 커넥션을 점유하기
 * 때문이다(A-3 교훈). 각 캐시 UPDATE는 단일 문장이라 그 자체로 원자적이고, 조회→채움은
 * 멱등(컬럼이 NULL일 때만 채움)이라 메서드 전역 트랜잭션이 필요 없다.
 *
 * <p><b>실패 정책(확정):</b> overview·휴무일 두 외부 호출 중 <b>하나라도 실패하면 502</b>.
 * {@link TourApiException}을 {@link CommonException}(502 계열)으로 변환해 전역 핸들러가
 * 깔끔한 JSON 에러로 응답하게 한다(TourLoadService와 동일 매핑).
 *
 * <p><b>센티넬:</b> overview/rest_date는 NULL=미조회 신호다. 외부가 값을 안 줘도 DAO가 ''로
 * 마킹해(빈 응답·휴무일 없는 타입) 다음 요청에서 재호출되지 않게 한다.
 */
@Service
@RequiredArgsConstructor
public class AttractionDetailService {

    private final AttractionDao attractionDao;
    private final TourApiClient tourApiClient;

    public AttractionDetailDto getDetail(Integer contentId) {
        AttractionDetailDto detail = attractionDao.selectDetailByContentId(contentId);
        if (detail == null) {
            throw new CommonException(ErrorCode.NOT_FOUND_ATTRACTION);
        }

        fillOverviewIfAbsent(contentId, detail);
        fillRestDateIfAbsent(contentId, detail);

        return detail;
    }

    /** overview가 NULL이면 detailCommon2로 overview/homepage를 채워 캐시한다. */
    private void fillOverviewIfAbsent(Integer contentId, AttractionDetailDto detail) {
        if (detail.getOverview() != null) {
            return;
        }
        DetailCommonItem common = fetchCommon(contentId);
        String overview = common != null ? common.getOverview() : null;
        String homepage = common != null ? common.homepageUrl() : null;

        attractionDao.updateOverviewCache(contentId, overview, homepage);
        // 캐시와 동일하게 응답 채움: overview는 '' 센티넬, homepage는 null 허용.
        detail.setOverview(overview != null ? overview : "");
        detail.setHomepage(homepage);
    }

    /** rest_date가 NULL이면 휴무일을 채운다. 휴무일 개념이 없는 타입은 호출 없이 '' 센티넬로 마킹. */
    private void fillRestDateIfAbsent(Integer contentId, AttractionDetailDto detail) {
        if (detail.getRestDate() != null) {
            return;
        }
        Integer typeId = detail.getContentTypeId();
        String restDate = null;
        if (DetailIntroItem.supportsRestDate(typeId)) {
            DetailIntroItem intro = fetchIntro(contentId, typeId);
            restDate = intro != null ? intro.restDateFor(typeId) : null;
        }

        attractionDao.updateRestDateCache(contentId, restDate);
        detail.setRestDate(restDate != null ? restDate : "");
    }

    private DetailCommonItem fetchCommon(Integer contentId) {
        try {
            return tourApiClient.fetchDetailCommon(contentId);
        } catch (TourApiException e) {
            throw toGatewayException(e);
        }
    }

    private DetailIntroItem fetchIntro(Integer contentId, Integer contentTypeId) {
        try {
            return tourApiClient.fetchDetailIntro(contentId, contentTypeId);
        } catch (TourApiException e) {
            throw toGatewayException(e);
        }
    }

    /** TourApiException 유형을 502 ErrorCode로 변환(TourLoadService와 동일 규칙). */
    private CommonException toGatewayException(TourApiException e) {
        ErrorCode code = switch (e.getErrorType()) {
            case QUOTA -> ErrorCode.TOUR_API_QUOTA_EXCEEDED;
            case KEY -> ErrorCode.TOUR_API_KEY_INVALID;
            default -> ErrorCode.TOUR_API_DETAIL_FAILED;
        };
        return new CommonException(code);
    }
}
