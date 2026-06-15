package com.ssafy.wswg.model.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ssafy.wswg.model.dto.AttractionDto;

@Mapper
public interface AttractionDao {
    int bulkUpsert(@Param("list") List<AttractionDto> attractions);
}
