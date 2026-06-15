package com.ssafy.wswg.model.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.wswg.model.dao.AttractionDao;
import com.ssafy.wswg.model.dto.AttractionDto;

/**
 * attractions 전체 리프레시 쓰기를 단 하나의 트랜잭션으로 묶는 전용 빈.
 *
 * <p><b>원자성:</b> {@link #write}는 {@code @Transactional} 한 메서드 안에서 청크별
 * {@code bulkUpsert}를 연속 호출한다. 즉 여러 INSERT...ON CONFLICT 문이 하나의
 * 트랜잭션에 묶이고, 도중 어느 청크가 실패하면 전체가 롤백돼 직전 정상(last-good)
 * 데이터가 보존된다.
 *
 * <p><b>HTTP는 호출 전에 끝나 있어야 한다:</b> 느린 외부 HTTP가 DB 커넥션·트랜잭션을
 * 점유하지 못하도록, 호출부(orchestrator)는 모든 페이지 fetch를 트랜잭션 <b>바깥</b>에서
 * 끝낸 뒤 변환된 DTO 목록만 이 메서드에 넘긴다. 이 메서드는 HTTP를 하지 않는다.
 *
 * <p>{@link RegionWriter}와 같은 이유로 별도 빈으로 분리한다(self-invocation 시
 * {@code @Transactional} 프록시 우회 방지).
 */
@Service
public class AttractionWriter {

    private final AttractionDao attractionDao;

    public AttractionWriter(AttractionDao attractionDao) {
        this.attractionDao = attractionDao;
    }

    /**
     * 변환된 attraction DTO 목록을 chunkSize 단위로 끊어 한 트랜잭션 안에서 upsert 한다.
     * 빈 목록이면 아무 것도 하지 않는다.
     */
    @Transactional
    public void write(List<AttractionDto> attractions, int chunkSize) {
        if (attractions == null || attractions.isEmpty()) {
            return;
        }
        int size = Math.max(1, chunkSize);
        for (int from = 0; from < attractions.size(); from += size) {
            int to = Math.min(from + size, attractions.size());
            attractionDao.bulkUpsert(attractions.subList(from, to));
        }
    }
}
