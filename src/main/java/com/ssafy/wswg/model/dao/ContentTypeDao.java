package com.ssafy.wswg.model.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.ssafy.wswg.model.dto.ContentTypeDto;

@Mapper
public interface ContentTypeDao {
    /** 콘텐츠타입(테마) 전체 조회. 고정 8종(정적 시드). 검색 필터 칩 구성용. */
    List<ContentTypeDto> selectAll();
}
