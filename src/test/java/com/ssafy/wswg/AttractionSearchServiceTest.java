package com.ssafy.wswg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;
import com.ssafy.wswg.model.dao.AttractionDao;
import com.ssafy.wswg.model.dto.AttractionSearchCondition;
import com.ssafy.wswg.model.dto.AttractionSummaryDto;
import com.ssafy.wswg.model.dto.PagedResponse;
import com.ssafy.wswg.model.service.AttractionSearchService;

/**
 * AttractionSearchService 단위 테스트(Mockito). DAO를 목으로 두고
 * 검증(page/size·구군-시도)·페이징 조립·범위초과 단축·keyword 정규화를 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class AttractionSearchServiceTest {

    @Mock
    AttractionDao attractionDao;

    @InjectMocks
    AttractionSearchService service;

    private AttractionSearchCondition cond(Integer sido, Integer gugun, String keyword) {
        return new AttractionSearchCondition(sido, gugun, null, keyword, false);
    }

    @Test
    @DisplayName("정상: count + LIMIT/OFFSET 조회를 조립해 PagedResponse 반환")
    void search_assemblesPagedResponse() {
        given(attractionDao.countSearch(any())).willReturn(5L);
        given(attractionDao.search(any(), eq(2), eq(0)))
                .willReturn(List.of(new AttractionSummaryDto(), new AttractionSummaryDto()));

        PagedResponse<AttractionSummaryDto> result = service.search(cond(null, null, null), 0, 2);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getPage()).isZero();
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getTotalElements()).isEqualTo(5L);
        verify(attractionDao).search(any(), eq(2), eq(0)); // offset = page*size = 0
    }

    @Test
    @DisplayName("범위 초과(offset >= total): 조회 쿼리 생략하고 빈 페이지 반환")
    void search_offsetBeyondTotal_skipsQuery() {
        given(attractionDao.countSearch(any())).willReturn(5L);

        PagedResponse<AttractionSummaryDto> result = service.search(cond(null, null, null), 10, 2);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(5L);
        verify(attractionDao, never()).search(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("검증: page 음수 → INVALID_PAGINATION, DAO 미호출")
    void search_negativePage_throws() {
        assertThatThrownBy(() -> service.search(cond(null, null, null), -1, 12))
                .isInstanceOf(CommonException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PAGINATION);
        verify(attractionDao, never()).countSearch(any());
    }

    @Test
    @DisplayName("검증: size < 1 → INVALID_PAGINATION")
    void search_zeroSize_throws() {
        assertThatThrownBy(() -> service.search(cond(null, null, null), 0, 0))
                .isInstanceOf(CommonException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PAGINATION);
    }

    @Test
    @DisplayName("구군만 있고 시도 없음: 거부하지 않고 그대로 조회(매퍼가 구군 필터 미적용, 명세 §2.4 준수)")
    void search_gugunWithoutSido_isLenient() {
        given(attractionDao.countSearch(any())).willReturn(0L);

        service.search(cond(null, 110, null), 0, 12);

        verify(attractionDao).countSearch(any()); // 400 없이 정상 조회 경로 진입
    }

    @Test
    @DisplayName("keyword 정규화: 공백뿐이면 null로 만들어 DAO에 전달")
    void search_blankKeyword_normalizedToNull() {
        given(attractionDao.countSearch(any())).willReturn(0L);

        service.search(cond(null, null, "   "), 0, 12);

        ArgumentCaptor<AttractionSearchCondition> captor =
                ArgumentCaptor.forClass(AttractionSearchCondition.class);
        verify(attractionDao).countSearch(captor.capture());
        assertThat(captor.getValue().getKeyword()).isNull();
    }
}
