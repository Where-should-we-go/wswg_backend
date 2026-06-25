package com.ssafy.wswg.model.dao;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.wswg.model.dto.MyPageTripResponse;
import com.ssafy.wswg.model.dto.TripDto;

@Mapper
public interface TripDao {
    List<MyPageTripResponse> readMyTrips(@Param("userId") Long userId, @Param("status") String status);

    List<MyPageTripResponse> readJoinedTrips(@Param("userId") Long userId, @Param("status") String status);

    int createTrip(TripDto trip);

    TripDto readTripById(Long tripId);

    List<TripDto> readTripsByUserId(Long userId);

    List<TripDto> readTripsByGroupId(@Param("groupId") Long groupId);

    int updateTrip(TripDto trip);

    int updateTripData(@Param("tripId") Long tripId, @Param("data") JsonNode data);

    int updateTripMeta(
            @Param("tripId") Long tripId,
            @Param("title") String title,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    int deleteTrip(Long tripId);
}
