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
}
