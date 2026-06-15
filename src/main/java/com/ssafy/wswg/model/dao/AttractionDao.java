package com.ssafy.wswg.model.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ssafy.wswg.model.dto.AttractionDto;
import com.ssafy.wswg.model.dto.AttractionSearchCondition;
import com.ssafy.wswg.model.dto.AttractionSummaryDto;
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

    /**
     * 동적 필터(시도/구군/콘텐츠타입/키워드) + 페이징으로 관광지를 검색한다.
     * sidos/guguns를 LEFT JOIN해 지역명을 채운다(코드가 null인 관광지도 누락 없이 포함).
     */
    List<AttractionSummaryDto> search(@Param("cond") AttractionSearchCondition cond,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /** search와 동일한 필터로 전체 매칭 건수를 센다(페이징 totalElements용). */
    long countSearch(@Param("cond") AttractionSearchCondition cond);
}
