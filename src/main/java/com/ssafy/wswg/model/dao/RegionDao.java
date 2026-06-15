package com.ssafy.wswg.model.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ssafy.wswg.model.dto.GugunDto;
import com.ssafy.wswg.model.dto.SidoDto;

@Mapper
public interface RegionDao {
    int upsertSidos(@Param("list") List<SidoDto> sidos);

    int upsertGuguns(@Param("list") List<GugunDto> guguns);

    /** 모든 시도 code. attractions FK 검증용 인메모리 세트 구성에 쓴다. */
    List<Integer> selectAllSidoCodes();

    /** 모든 시군구. attractions 복합 FK 검증용 인메모리 세트 구성에 쓴다. */
    List<GugunDto> selectAllGuguns();

    /** 시도 전체 조회(검색 화면 시/도 드롭다운용). */
    List<SidoDto> selectSidos();

    /** 특정 시도에 속한 시군구 조회(검색 화면 구/군 드롭다운용). */
    List<GugunDto> selectGugunsBySido(@Param("sidoCode") int sidoCode);
}
