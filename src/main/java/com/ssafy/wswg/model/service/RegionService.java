package com.ssafy.wswg.model.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.wswg.model.dao.ContentTypeDao;
import com.ssafy.wswg.model.dao.RegionDao;
import com.ssafy.wswg.model.dto.ContentTypeDto;
import com.ssafy.wswg.model.dto.GugunDto;
import com.ssafy.wswg.model.dto.SidoDto;

import lombok.RequiredArgsConstructor;

/**
 * 검색 화면(S3)의 필터 참조 데이터 조회 서비스.
 *
 * <p>시/도·시군구·콘텐츠타입(테마)은 모두 우리 DB에 적재된 정적 마스터 데이터를
 * 그대로 읽어 내려주는 단순 조회다(외부 TourAPI 호출 없음). 결과가 없으면 빈 목록.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionService {
    private final RegionDao regionDao;
    private final ContentTypeDao contentTypeDao;

    /** 시도 전체 조회. */
    public List<SidoDto> getSidos() {
        return regionDao.selectSidos();
    }

    /** 특정 시도의 시군구 조회. 존재하지 않는 시도면 빈 목록. */
    public List<GugunDto> getGuguns(int sidoCode) {
        return regionDao.selectGugunsBySido(sidoCode);
    }

    /** 콘텐츠타입(테마) 전체 조회. */
    public List<ContentTypeDto> getContentTypes() {
        return contentTypeDao.selectAll();
    }
}
