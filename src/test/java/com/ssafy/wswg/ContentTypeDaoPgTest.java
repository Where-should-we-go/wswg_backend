package com.ssafy.wswg;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ssafy.wswg.model.dao.ContentTypeDao;
import com.ssafy.wswg.model.dto.ContentTypeDto;

/**
 * ContentTypeDao가 schema.sql의 정적 시드(콘텐츠타입 8종)를 읽어오는지 검증.
 * 시드는 initdb로 테스트 트랜잭션 밖에서 커밋돼 있으므로 별도 삽입 없이 조회만 한다.
 */
class ContentTypeDaoPgTest extends AbstractPostgisIntegrationTest {

    @Autowired
    ContentTypeDao contentTypeDao;

    @Test
    @DisplayName("selectAll: 시드된 콘텐츠타입 8종을 content_type_id 오름차순으로 반환")
    void selectAll_returnsSeededTypes() {
        assertThat(contentTypeDao.selectAll())
                .extracting(ContentTypeDto::getContentTypeId)
                .containsExactly(12, 14, 15, 25, 28, 32, 38, 39); // ORDER BY content_type_id

        assertThat(contentTypeDao.selectAll())
                .filteredOn(t -> t.getContentTypeId() == 12)
                .extracting(ContentTypeDto::getContentTypeName)
                .containsExactly("관광지");
    }
}
