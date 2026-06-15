package com.ssafy.wswg.model.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ssafy.wswg.model.dto.GugunDto;
import com.ssafy.wswg.model.dto.SidoDto;
import com.ssafy.wswg.external.tour.TourApiClient;
import com.ssafy.wswg.external.tour.dto.LdongItem;

/**
 * 지역 마스터(시도/시군구) 적재 오케스트레이터.
 *
 * <p><b>fetch-바깥 / write-안 분리:</b> 모든 TourAPI HTTP 호출(시도 1회 + 시도별
 * 시군구 N회)을 먼저 트랜잭션 <b>바깥</b>에서 끝내 메모리에 모은 뒤, 마지막에
 * {@link RegionWriter#write}로 단 한 번만 트랜잭션 <b>안</b>에서 DB에 쓴다. 느린
 * 외부 HTTP가 DB 트랜잭션·커넥션을 점유하는 것을 막고, 쓰기는 한 트랜잭션으로
 * 원자화한다. 이 빈 자체에는 {@code @Transactional}을 두지 않는다.
 */
@Service
public class RegionLoader {

    private final TourApiClient tourApiClient;
    private final RegionWriter regionWriter;

    public RegionLoader(TourApiClient tourApiClient, RegionWriter regionWriter) {
        this.tourApiClient = tourApiClient;
        this.regionWriter = regionWriter;
    }

    /**
     * 시도/시군구를 TourAPI에서 받아 한 트랜잭션으로 적재한다.
     *
     * <p>시군구 응답 item에는 부모 시도 code가 없으므로, GugunDto.sidoCode는
     * 조회에 사용한 부모 시도 code로 직접 채운다.
     */
    public RegionLoadResult load() {
        // a. 시도 fetch (HTTP, 트랜잭션 밖) → SidoDto 매핑
        List<SidoDto> sidos = new ArrayList<>();
        List<GugunDto> guguns = new ArrayList<>();

        for (LdongItem sidoItem : tourApiClient.fetchSidos()) {
            int sidoCode = Integer.parseInt(sidoItem.getCode());
            sidos.add(new SidoDto(sidoCode, sidoItem.getName()));

            // b. 시도별 시군구 fetch (HTTP, 트랜잭션 밖) → GugunDto 매핑
            //    응답에 부모 시도 code가 없으니 sidoCode는 조회한 부모 code로 채운다.
            for (LdongItem gugunItem : tourApiClient.fetchGuguns(sidoCode)) {
                guguns.add(new GugunDto(
                        sidoCode,
                        Integer.parseInt(gugunItem.getCode()),
                        gugunItem.getName()));
            }
        }

        // c. 유일한 트랜잭션 단계: HTTP는 이미 모두 끝난 상태.
        regionWriter.write(sidos, guguns);

        // d. 적재 건수 반환
        return new RegionLoadResult(sidos.size(), guguns.size());
    }

    /** 적재 결과 요약. */
    public record RegionLoadResult(int sidoCount, int gugunCount) {
    }
}
