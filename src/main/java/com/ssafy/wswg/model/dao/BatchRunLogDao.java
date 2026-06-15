package com.ssafy.wswg.model.dao;

import org.apache.ibatis.annotations.Mapper;

import com.ssafy.wswg.model.dto.BatchRunLog;

@Mapper
public interface BatchRunLogDao {
    int insertLog(BatchRunLog log);
}
