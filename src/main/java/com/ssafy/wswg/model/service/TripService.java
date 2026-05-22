package com.ssafy.wswg.model.service;

import com.ssafy.wswg.model.dao.TripDao;
import com.ssafy.wswg.model.dto.AttractionDto;
import com.ssafy.wswg.model.dto.GugunDto;
import com.ssafy.wswg.model.dto.SidoDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true) // 기본적으로 모든 메서드를 읽기 전용 트랜잭션으로 설정 (성능 최적화)
public class TripService {

    private final TripDao tripDao;

    public TripService(TripDao tripDao) {
        this.tripDao = tripDao;
    }

    // 1. 시도 조회
    public List<SidoDto> readSidos() {
        try {
            return tripDao.readSidos();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>(); // 에러 발생 시 빈 리스트 반환 (기존 로직 유지)
        }
    }

    // 2. 구군 조회
    public List<GugunDto> readGuguns(int sidoCode) {
        try {
            return tripDao.readGuguns(sidoCode);
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // 3. 명소 등록 (C, U, D 작업에는 @Transactional을 붙여 데이터 일관성을 보장합니다)
    @Transactional
    public int createAttraction(AttractionDto attraction) {
        try {
            return tripDao.createAttraction(attraction);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    // 4. 명소 단건 조회
    public AttractionDto readAttraction(int no) {
        try {
            return tripDao.readAttraction(no);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 5. 명소 목록 조회
    public List<AttractionDto> readAttractions() {
        try {
            return tripDao.readAttractions();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // 6. 명소 수정
    @Transactional
    public int updateAttraction(AttractionDto attraction) {
        try {
            return tripDao.updateAttraction(attraction);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    // 7. 명소 삭제
    @Transactional
    public int deleteAttraction(int no) {
        try {
            return tripDao.deleteAttraction(no);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
}