package com.ssafy.wswg.model.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ssafy.wswg.model.dto.AttractionDto;
import com.ssafy.wswg.model.dto.NearbyAttractionDto;
import com.ssafy.wswg.model.dto.SemanticAttractionDto;

@Mapper
public interface AttractionDao {
    int bulkUpsert(@Param("list") List<AttractionDto> attractions);

    List<NearbyAttractionDto> findNearbyAttractions(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusMeters") int radiusMeters,
            @Param("contentTypeId") Integer contentTypeId,
            @Param("limit") int limit);

    List<SemanticAttractionDto> findSemanticNearbyAttractions(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusMeters") int radiusMeters,
            @Param("contentTypeId") Integer contentTypeId,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("embeddingModel") String embeddingModel,
            @Param("limit") int limit);
}
