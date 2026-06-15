package com.ssafy.wswg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;
import com.ssafy.wswg.external.tour.TourApiClient;
import com.ssafy.wswg.external.tour.TourApiException;
import com.ssafy.wswg.external.tour.TourApiException.TourApiErrorType;
import com.ssafy.wswg.external.tour.dto.DetailCommonItem;
import com.ssafy.wswg.external.tour.dto.DetailIntroItem;
import com.ssafy.wswg.model.dao.AttractionDao;
import com.ssafy.wswg.model.dto.AttractionDetailDto;
import com.ssafy.wswg.model.service.AttractionDetailService;

/**
 * AttractionDetailService 단위 테스트(Mockito). DAO·TourApiClient를 목으로 두고
 * 404 / lazy fill write-through / 캐시 히트 시 외부 미호출 / 휴무일 없는 타입 센티넬 /
 * 외부 실패 시 502 매핑(하나라도 실패→502)을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AttractionDetailServiceTest {

    static final int CID = 126508;

    @Mock
    AttractionDao attractionDao;

    @Mock
    TourApiClient tourApiClient;

    @InjectMocks
    AttractionDetailService service;

    private AttractionDetailDto dto(Integer contentTypeId, String overview, String restDate) {
        AttractionDetailDto d = new AttractionDetailDto();
        d.setContentId(CID);
        d.setContentTypeId(contentTypeId);
        d.setOverview(overview);
        d.setRestDate(restDate);
        return d;
    }

    private DetailCommonItem common(String overview, String homepage) {
        DetailCommonItem c = new DetailCommonItem();
        c.setOverview(overview);
        c.setHomepage(homepage);
        return c;
    }

    @Test
    @DisplayName("없는 contentId → 404(NOT_FOUND_ATTRACTION), 외부 호출 없음")
    void notFound() {
        given(attractionDao.selectDetailByContentId(CID)).willReturn(null);

        assertThatThrownBy(() -> service.getDetail(CID))
                .isInstanceOf(CommonException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND_ATTRACTION);
        verify(tourApiClient, never()).fetchDetailCommon(anyInt());
        verify(tourApiClient, never()).fetchDetailIntro(anyInt(), anyInt());
    }

    @Test
    @DisplayName("lazy fill: overview/휴무일 모두 NULL(관광지12) → 두 외부 호출 + 캐시 + 응답 채움")
    void lazyFillBoth() {
        given(attractionDao.selectDetailByContentId(CID)).willReturn(dto(12, null, null));
        given(tourApiClient.fetchDetailCommon(CID))
                .willReturn(common("조선의 법궁", "<a href=\"http://royal.kr\">x</a>"));
        DetailIntroItem intro = new DetailIntroItem();
        intro.setRestdate("매주 화요일");
        given(tourApiClient.fetchDetailIntro(CID, 12)).willReturn(intro);

        AttractionDetailDto result = service.getDetail(CID);

        assertThat(result.getOverview()).isEqualTo("조선의 법궁");
        assertThat(result.getHomepage()).isEqualTo("http://royal.kr");  // 앵커 href 추출
        assertThat(result.getRestDate()).isEqualTo("매주 화요일");
        verify(attractionDao).updateOverviewCache(CID, "조선의 법궁", "http://royal.kr");
        verify(attractionDao).updateRestDateCache(CID, "매주 화요일");
    }

    @Test
    @DisplayName("캐시 히트: overview·휴무일 모두 있음 → 외부 호출/캐시 UPDATE 없음")
    void cacheHit() {
        given(attractionDao.selectDetailByContentId(CID)).willReturn(dto(12, "이미 있음", "연중무휴"));

        AttractionDetailDto result = service.getDetail(CID);

        assertThat(result.getOverview()).isEqualTo("이미 있음");
        verify(tourApiClient, never()).fetchDetailCommon(anyInt());
        verify(tourApiClient, never()).fetchDetailIntro(anyInt(), anyInt());
        verify(attractionDao, never()).updateOverviewCache(any(), any(), any());
        verify(attractionDao, never()).updateRestDateCache(any(), any());
    }

    @Test
    @DisplayName("휴무일 없는 타입(축제15): detailIntro2 미호출, rest_date는 '' 센티넬로 마킹")
    void unsupportedTypeMarksSentinel() {
        // overview는 이미 채워둬 detailCommon 경로를 격리(휴무일 경로만 검증)
        given(attractionDao.selectDetailByContentId(CID)).willReturn(dto(15, "개요있음", null));

        AttractionDetailDto result = service.getDetail(CID);

        verify(tourApiClient, never()).fetchDetailIntro(anyInt(), anyInt());
        verify(attractionDao).updateRestDateCache(CID, null);   // 매퍼가 ''로 저장
        assertThat(result.getRestDate()).isEmpty();
    }

    @Test
    @DisplayName("외부 실패(QUOTA) → 502 TOUR_API_QUOTA_EXCEEDED")
    void overviewQuotaFailureMapsTo502() {
        given(attractionDao.selectDetailByContentId(CID)).willReturn(dto(12, null, "연중무휴"));
        given(tourApiClient.fetchDetailCommon(CID))
                .willThrow(new TourApiException(TourApiErrorType.QUOTA, false, "22", "quota"));

        assertThatThrownBy(() -> service.getDetail(CID))
                .isInstanceOf(CommonException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TOUR_API_QUOTA_EXCEEDED);
    }

    @Test
    @DisplayName("외부 실패(일시/UNKNOWN) → 502 TOUR_API_DETAIL_FAILED")
    void overviewGenericFailureMapsTo502() {
        given(attractionDao.selectDetailByContentId(CID)).willReturn(dto(12, null, "연중무휴"));
        given(tourApiClient.fetchDetailCommon(CID))
                .willThrow(new TourApiException(TourApiErrorType.UNKNOWN, false, null, "boom"));

        assertThatThrownBy(() -> service.getDetail(CID))
                .isInstanceOf(CommonException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TOUR_API_DETAIL_FAILED);
    }

    @Test
    @DisplayName("휴무일 호출 실패도 502(하나라도 실패→502)")
    void introFailureMapsTo502() {
        // overview는 채워둬 휴무일 호출만 실패시킴
        given(attractionDao.selectDetailByContentId(CID)).willReturn(dto(12, "개요있음", null));
        given(tourApiClient.fetchDetailIntro(CID, 12))
                .willThrow(new TourApiException(TourApiErrorType.TRANSIENT, true, "01", "transient"));

        assertThatThrownBy(() -> service.getDetail(CID))
                .isInstanceOf(CommonException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TOUR_API_DETAIL_FAILED);
    }
}
