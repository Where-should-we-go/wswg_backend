package com.ssafy.wswg;

import org.junit.jupiter.api.Test;

/**
 * 컨텍스트 로드 스모크 테스트.
 * Testcontainers PostGIS 위에서 전체 Spring 컨텍스트가 뜨는지 확인한다.
 */
class WswgApplicationTests extends AbstractPostgisIntegrationTest {

	@Test
	void contextLoads() {
	}

}
