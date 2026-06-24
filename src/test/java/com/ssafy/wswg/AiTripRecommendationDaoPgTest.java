package com.ssafy.wswg;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ssafy.wswg.model.dao.AttractionDao;
import com.ssafy.wswg.model.dto.AiTripRecommendationMatchDto;

class AiTripRecommendationDaoPgTest extends AbstractPostgisIntegrationTest {
    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AttractionDao attractionDao;

    @Test
    void findAiTripRecommendationMatches_ordersByVectorSimilarityAndReturnsDistance() {
        insertRegion(11, "서울", 110, "종로구");
        insertAttraction(1001, "경복궁", 11, 110, 37.5759, 126.9768);
        insertAttraction(1002, "해운대", 11, 110, 37.5700, 126.9700);
        insertEmbedding(1001, vectorLiteral(1.0, 0.0));
        insertEmbedding(1002, vectorLiteral(0.0, 1.0));

        List<AiTripRecommendationMatchDto> matches = attractionDao.findAiTripRecommendationMatches(
                37.5665,
                126.9780,
                50_000,
                null,
                vectorLiteral(1.0, 0.0),
                "text-embedding-3-small",
                2);

        assertThat(matches).hasSize(2);
        assertThat(matches).extracting(AiTripRecommendationMatchDto::getContentId)
                .containsExactly(1001, 1002);
        assertThat(matches.get(0).getSimilarity()).isGreaterThan(matches.get(1).getSimilarity());
        assertThat(matches.get(0).getDistanceMeters()).isNotNull();
        assertThat(matches.get(0).getSidoName()).isEqualTo("서울");
        assertThat(matches.get(0).getGugunName()).isEqualTo("종로구");
    }

    private void insertRegion(Integer sidoCode, String sidoName, Integer gugunCode, String gugunName) {
        jdbc.update(
                "INSERT INTO sidos (sido_code, sido_name) VALUES (?, ?) ON CONFLICT DO NOTHING",
                sidoCode,
                sidoName);
        jdbc.update(
                "INSERT INTO guguns (sido_code, gugun_code, gugun_name) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                sidoCode,
                gugunCode,
                gugunName);
    }

    private void insertAttraction(
            Integer contentId,
            String title,
            Integer sidoCode,
            Integer gugunCode,
            Double latitude,
            Double longitude) {
        jdbc.update(
                "INSERT INTO attractions (content_id, title, content_type_id, sido_code, gugun_code, latitude, longitude) "
                        + "VALUES (?, ?, 12, ?, ?, ?, ?)",
                contentId,
                title,
                sidoCode,
                gugunCode,
                latitude,
                longitude);
    }

    private void insertEmbedding(Integer contentId, String embedding) {
        jdbc.update(
                "INSERT INTO attraction_embeddings (content_id, embedding, embedding_text, embedding_model) "
                        + "VALUES (?, ?::vector, ?, 'text-embedding-3-small')",
                contentId,
                embedding,
                "embedding " + contentId);
    }

    private String vectorLiteral(double first, double second) {
        StringBuilder builder = new StringBuilder("[");
        builder.append(first).append(',').append(second);
        for (int i = 2; i < 1536; i++) {
            builder.append(",0.0");
        }
        return builder.append(']').toString();
    }
}
