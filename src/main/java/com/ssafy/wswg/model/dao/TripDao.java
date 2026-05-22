package com.ssafy.wswg.model.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ssafy.wswg.model.dto.AttractionDto;
import com.ssafy.wswg.model.dto.GugunDto;
import com.ssafy.wswg.model.dto.SidoDto;

@Mapper // Spring-MyBatis가 자동으로 구현체를 생성하여 빈(Bean)으로 등록합니다.
public interface TripDao {

    // 명소 정보 등록
    int createAttraction(AttractionDto attraction);
    
    // 명소 정보 조회
    AttractionDto readAttraction(int no);
    
    // 명소 정보 수정
    int updateAttraction(AttractionDto attraction);
    
    // 명소 정보 삭제
    int deleteAttraction(int no);
    
    // 명소 목록 조회
    List<AttractionDto> readAttractions();
    
    // 시도 조회 (Connection이 없어도 이제 깔끔하게 동작합니다)
    List<SidoDto> readSidos();
    
    // 군구 조회 (파라미터가 명확해집니다)
    List<GugunDto> readGuguns(@Param("sidoCode") int sidoCode);
}