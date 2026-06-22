package com.ssafy.wswg.model.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ssafy.wswg.model.dto.MyPageTripResponse;

@Mapper
public interface TripDao {
    List<MyPageTripResponse> readMyTrips(@Param("userId") Long userId, @Param("status") String status);

    List<MyPageTripResponse> readJoinedTrips(@Param("userId") Long userId, @Param("status") String status);
}
