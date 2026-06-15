package com.ssafy.wswg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ssafy.wswg.model.dto.GugunDto;
import com.ssafy.wswg.model.dto.SidoDto;
import com.ssafy.wswg.model.service.RegionLoader;
import com.ssafy.wswg.model.service.RegionLoader.RegionLoadResult;
import com.ssafy.wswg.model.service.RegionWriter;
import com.ssafy.wswg.external.tour.TourApiClient;
import com.ssafy.wswg.external.tour.dto.LdongItem;

/**
 * RegionLoader 매핑/오케스트레이션 단위 테스트(Docker 불필요).
 * TourApiClient와 RegionWriter를 Mockito로 목킹해, code parseInt 매핑과
 * 시군구의 부모 시도 code 전파(GugunDto.sidoCode = 조회한 시도 code)를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class RegionLoaderTest {

    @Mock
    TourApiClient tourApiClient;

    @Mock
    RegionWriter regionWriter;

    @InjectMocks
    RegionLoader regionLoader;

    @Captor
    ArgumentCaptor<List<SidoDto>> sidosCaptor;

    @Captor
    ArgumentCaptor<List<GugunDto>> gugunsCaptor;

    @Test
    @DisplayName("시도/시군구 매핑 + 시군구 sidoCode 부모 전파 + RegionWriter.write 호출")
    void load_mapsAndPropagatesParentSidoCode() {
        // 시도 2개
        when(tourApiClient.fetchSidos()).thenReturn(List.of(
                new LdongItem(1, "11", "서울특별시"),
                new LdongItem(2, "26", "부산광역시")));
        // 시도별 시군구 (응답 item에는 부모 시도 code가 없음)
        when(tourApiClient.fetchGuguns(11)).thenReturn(List.of(
                new LdongItem(1, "110", "종로구"),
                new LdongItem(2, "140", "중구")));
        when(tourApiClient.fetchGuguns(26)).thenReturn(List.of(
                new LdongItem(1, "110", "중구")));

        RegionLoadResult result = regionLoader.load();

        // 반환 건수
        assertThat(result.sidoCount()).isEqualTo(2);
        assertThat(result.gugunCount()).isEqualTo(3);

        // 시도별로 fetchGuguns가 정확히 1회씩 호출
        verify(tourApiClient, times(1)).fetchGuguns(eq(11));
        verify(tourApiClient, times(1)).fetchGuguns(eq(26));

        // RegionWriter.write가 수집된 전체 리스트로 호출됐는지 캡처해 검증
        verify(regionWriter).write(sidosCaptor.capture(), gugunsCaptor.capture());

        List<SidoDto> sidos = sidosCaptor.getValue();
        assertThat(sidos).extracting(SidoDto::getSidoCode).containsExactly(11, 26);
        assertThat(sidos).extracting(SidoDto::getSidoName)
                .containsExactly("서울특별시", "부산광역시");

        List<GugunDto> guguns = gugunsCaptor.getValue();
        assertThat(guguns).hasSize(3);
        // 핵심: 시군구 sidoCode는 조회한 부모 시도 code로 채워져야 한다.
        assertThat(guguns).extracting(GugunDto::getSidoCode).containsExactly(11, 11, 26);
        assertThat(guguns).extracting(GugunDto::getGugunCode).containsExactly(110, 140, 110);
        assertThat(guguns).extracting(GugunDto::getGugunName)
                .containsExactly("종로구", "중구", "중구");
    }
}
