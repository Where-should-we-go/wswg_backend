package com.ssafy.wswg;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ssafy.wswg.model.dto.AttractionDto;
import com.ssafy.wswg.model.dto.GugunDto;
import com.ssafy.wswg.model.dto.SidoDto;
import com.ssafy.wswg.model.service.TripService;
import com.ssafy.wswg.util.DBUtil;

@SpringBootTest
class TripServiceTest {

    @Autowired
    private TripService tripService;

    // 테스트용 고유 아이디
    private static final int TEST_CONTENT_ID = 999999;

    @AfterEach
    void tearDown() {
        // 매 테스트 종료 후 테스트 데이터 깔끔하게 삭제
        String sql = "DELETE FROM attractions WHERE content_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, TEST_CONTENT_ID);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private AttractionDto createTestDto(String title) {
        return new AttractionDto(
                0, TEST_CONTENT_ID, title, 12, 1, 1, // area_code 1, si_gun_gu_code 1 적용
                null, null, 6, 37.5665, 126.9780, 
                "02-000-0000", "서울 테스트 주소", "상세 주소", "http://test.com", "테스트 설명"
        );
    }

    @Test
    @DisplayName("시도 목록 조회")
    void readSidos() {
        List<SidoDto> sidos = tripService.readSidos();
        assertThat(sidos).isNotNull();
        assertThat(sidos).isNotEmpty();
    }

    @Test
    @DisplayName("구군 목록 조회")
    void readGuguns() {
        List<GugunDto> guguns = tripService.readGuguns(1); // 서울(1) 기준
        assertThat(guguns).isNotNull();
        assertThat(guguns).isNotEmpty();
    }

    @Test
    @DisplayName("관광지 등록 CREATE")
    void createAttraction() {
        AttractionDto attraction = createTestDto("테스트 관광지");
        int result = tripService.createAttraction(attraction);
        assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("관광지 목록 조회 READ ALL")
    void readAttractions() {
        tripService.createAttraction(createTestDto("목록 조회 테스트용"));
        List<AttractionDto> attractions = tripService.readAttractions();
        assertThat(attractions).isNotNull();
        assertThat(attractions).isNotEmpty();
    }

    @Test
    @DisplayName("관광지 단건 조회 READ ONE")
    void readAttraction() {
        // 1. 등록
        tripService.createAttraction(createTestDto("단건 조회 테스트용"));
        
        // 2. 등록된 데이터의 진짜 'no'를 알아내기 위해 목록에서 찾음
        AttractionDto saved = tripService.readAttractions().stream()
                .filter(a -> a.getContentId() == TEST_CONTENT_ID)
                .findFirst()
                .orElseThrow();

        // 3. 단건 조회 테스트
        AttractionDto found = tripService.readAttraction(saved.getNo());
        assertThat(found).isNotNull();
        assertThat(found.getTitle()).isEqualTo("단건 조회 테스트용");
    }

    @Test
    @DisplayName("관광지 수정 UPDATE")
    void updateAttraction() {
        // 1. 등록
        tripService.createAttraction(createTestDto("수정 전 관광지"));

        // 2. 진짜 'no'가 포함된 객체를 DB에서 꺼내옴 (★여기가 핵심!)
        AttractionDto saved = tripService.readAttractions().stream()
                .filter(a -> a.getContentId() == TEST_CONTENT_ID)
                .findFirst()
                .orElseThrow();

        // 3. 객체 내용 수정 후 DB 업데이트
        saved.setTitle("수정 후 관광지");
        saved.setAddr1("수정 후 주소");
        int result = tripService.updateAttraction(saved);

        // 4. 검증
        assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("관광지 삭제 DELETE")
    void deleteAttraction() {
        // 1. 등록
        tripService.createAttraction(createTestDto("삭제 테스트 관광지"));

        // 2. 진짜 'no' 알아내기
        AttractionDto saved = tripService.readAttractions().stream()
                .filter(a -> a.getContentId() == TEST_CONTENT_ID)
                .findFirst()
                .orElseThrow();

        // 3. 삭제
        int result = tripService.deleteAttraction(saved.getNo());

        // 4. 검증
        assertThat(result).isEqualTo(1);
    }
}