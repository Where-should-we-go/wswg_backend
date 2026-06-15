package com.ssafy.wswg.model.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.wswg.model.dao.RegionDao;
import com.ssafy.wswg.model.dto.GugunDto;
import com.ssafy.wswg.model.dto.SidoDto;

/**
 * 지역 마스터(시도/시군구) 쓰기를 한 트랜잭션으로 묶는 전용 빈.
 *
 * <p><b>별도 빈으로 분리한 이유:</b> {@link RegionLoader}는 HTTP fetch를 트랜잭션
 * <b>바깥</b>에서 수행해야 한다(느린 외부 호출 동안 DB 커넥션·트랜잭션을 점유하지
 * 않기 위함). Spring AOP의 {@code @Transactional}은 빈 경계를 넘는 호출에만 적용되고
 * 같은 객체 내부 호출(self-invocation)은 프록시를 우회하므로, 트랜잭션 경계를
 * 별도 빈인 여기에 둔다. 시도/시군구 upsert를 모두 한 트랜잭션으로 처리해
 * all-or-nothing 원자성을 보장한다.
 */
@Service
public class RegionWriter {

    private final RegionDao regionDao;

    public RegionWriter(RegionDao regionDao) {
        this.regionDao = regionDao;
    }

    /**
     * 시도/시군구를 한 트랜잭션으로 upsert 한다. 빈 리스트는 호출을 건너뛴다
     * (foreach가 빈 컬렉션을 만나면 INSERT ... VALUES 뒤가 비어 SQL이 깨지므로).
     */
    @Transactional
    public void write(List<SidoDto> sidos, List<GugunDto> guguns) {
        if (sidos != null && !sidos.isEmpty()) {
            regionDao.upsertSidos(sidos);
        }
        if (guguns != null && !guguns.isEmpty()) {
            regionDao.upsertGuguns(guguns);
        }
    }
}
