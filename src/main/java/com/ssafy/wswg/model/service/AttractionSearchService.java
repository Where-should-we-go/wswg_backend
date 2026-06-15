package com.ssafy.wswg.model.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;
import com.ssafy.wswg.model.dao.AttractionDao;
import com.ssafy.wswg.model.dto.AttractionSearchCondition;
import com.ssafy.wswg.model.dto.AttractionSummaryDto;
import com.ssafy.wswg.model.dto.PagedResponse;

import lombok.RequiredArgsConstructor;

/**
 * 관광지 검색 서비스. 동적 필터 + 페이징으로 우리 DB의 관광지를 조회한다(외부 TourAPI 호출 없음).
 *
 * <p>검증: page는 0 이상, size는 1 이상(위반 시 400). 그 외 필터는 모두 선택적이며
 * 매칭이 없으면 빈 목록(200)을 준다. 구군만 있고 시도가 없으면 매퍼가 구군 필터를 적용하지
 * 않으므로(복합키라 시도 없이는 모호) 거부하지 않고 그대로 조회한다 — 명세서 §2.4 예외 목록 준수.
 *
 * <p>페이징은 COUNT 1회 + LIMIT/OFFSET 조회 1회로 수동 조립한다(MyBatis라 Spring Data Page 없음).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttractionSearchService {
    private final AttractionDao attractionDao;

    public PagedResponse<AttractionSummaryDto> search(AttractionSearchCondition cond, int page, int size) {
        validate(page, size);
        normalizeKeyword(cond);

        long totalElements = attractionDao.countSearch(cond);

        // 요청 페이지가 전체 범위를 벗어나면 빈 페이지(조회 쿼리 생략 + offset int 오버플로 방지).
        long offset = (long) page * size;
        List<AttractionSummaryDto> content = offset >= totalElements
                ? List.of()
                : attractionDao.search(cond, size, (int) offset);

        return new PagedResponse<>(content, page, size, totalElements);
    }

    private void validate(int page, int size) {
        if (page < 0 || size < 1) {
            throw new CommonException(ErrorCode.INVALID_PAGINATION);
        }
    }

    /** 공백뿐인 keyword는 필터에서 제외(빈 ILIKE 패턴 방지). */
    private void normalizeKeyword(AttractionSearchCondition cond) {
        if (cond.getKeyword() != null && cond.getKeyword().isBlank()) {
            cond.setKeyword(null);
        }
    }
}
