package com.ssafy.wswg.model.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ssafy.wswg.model.dto.AttractionDetailDto;
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

    /**
     * 관광지 단건 상세(A-6). sidos/guguns/contenttypes를 LEFT JOIN해 이름 3종을 채운다
     * (코드가 null인 관광지도 누락 없이 반환, 미매칭 이름은 null). 없는 contentId면 null.
     */
    AttractionDetailDto selectDetailByContentId(@Param("contentId") Integer contentId);

    /**
     * A-6 write-through: detailCommon2로 받은 overview/homepage를 캐시한다(다른 컬럼은 건드리지 않음).
     * 외부 HTTP는 트랜잭션 밖에서 끝낸 뒤 이 UPDATE만 짧게 실행한다.
     */
    int updateOverviewCache(@Param("contentId") Integer contentId,
            @Param("overview") String overview,
            @Param("homepage") String homepage);

    /**
     * A-6 write-through: detailIntro2로 받은 휴무일을 rest_date에 캐시한다(다른 컬럼 미변경).
     * NULL=미조회 센티넬이라, 빈 값/휴무일 없는 타입도 ''로 마킹해 재호출을 막는다.
     */
    int updateRestDateCache(@Param("contentId") Integer contentId,
            @Param("restDate") String restDate);
}
