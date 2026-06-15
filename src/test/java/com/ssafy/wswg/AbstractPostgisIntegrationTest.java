package com.ssafy.wswg;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * 모든 통합 테스트의 베이스.
 *
 * <p>진짜 PostgreSQL(PostGIS 포함)을 Testcontainers로 띄운다. 스키마가 PostGIS
 * geometry / GENERATED ALWAYS STORED / JSONB / ON CONFLICT 등 PG 전용 기능을
 * 쓰므로 H2로는 검증 불가하기 때문이다.
 *
 * <p><b>진짜 싱글톤 컨테이너 패턴.</b> 컨테이너는 {@code static} 필드로 선언하고 static
 * 초기화 블록에서 <b>딱 한 번 {@code start()}</b> 한다. {@code @Container}/{@code @Testcontainers}
 * JUnit5 확장은 <b>쓰지 않는다</b>: 그 확장은 <b>테스트 클래스마다</b> 컨테이너를 새로
 * 띄우고 클래스가 끝나면 멈춘다. 그런데 Spring은 동일 설정의 ApplicationContext를
 * <b>캐시·재사용</b>하므로, 첫 클래스가 잡은 컨테이너 포트가 HikariPool에 박힌 채
 * 이후 클래스에서 그 컨테이너가 이미 정지돼 'Connection refused'가 났다(실제 관측된 버그).
 * static 블록에서 한 번만 띄우고 끝까지 살려두면(JVM 종료 시 Ryuk가 정리) 캐시된
 * 컨텍스트도 항상 살아 있는 동일 컨테이너를 가리킨다. {@link DynamicPropertySource}로
 * 컨테이너의 jdbc-url/계정을 Spring DataSource에 주입한다.
 *
 * <p>스키마 적용은 postgres 이미지의 init 메커니즘({@code /docker-entrypoint-initdb.d/*.sql})으로
 * 컨테이너 최초 기동 시 진짜 psql이 실행한다(아래 {@code withCopyFileToContainer}).
 * Spring의 {@code spring.sql.init}은 PL/pgSQL의 {@code $$} 달러쿼팅을 못 다뤄
 * 'Unterminated dollar quote'로 실패하므로 사용하지 않는다
 * (postgis 확장 생성 + 9테이블 + contenttypes 8종 시드).
 *
 * <p>Docker가 없으면 static 블록의 {@code start()}가 예외를 던져 컨텍스트 로드가
 * <b>에러로 실패(fail loud)</b>한다 — 이 프로젝트는 Redis·PostGIS를 모두 Docker로 띄우는 게
 * <b>필수 인프라</b>라, Docker가 꺼진 상태는 "정상"이 아니라 고쳐야 할 상태이기 때문이다
 * (기존 {@code disabledWithoutDocker = false}와 동일한 의도).
 */
// 컨테이너가 JVM 내내 살아있는 싱글톤이라(스키마는 최초 1회만 적용), 각 테스트가
// insert한 행을 정리하지 않으면 같은 JVM 재실행(IDE 재시도/JUnit retry) 시 고정 PK·
// 이메일이 중복키로 충돌한다. @Transactional로 테스트 메서드마다 자동 롤백해 누수 방지.
// (init 시드 contenttypes는 테스트 트랜잭션 밖에서 커밋돼 그대로 유지된다.)
@SpringBootTest(classes = WswgApplication.class)
@Transactional
public abstract class AbstractPostgisIntegrationTest {

    /** PostGIS 16-3.4 이미지. postgres 와 호환되도록 asCompatibleSubstituteFor 지정. */
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("wswg")
            .withUsername("wswg")
            .withPassword("wswg")
            // 스키마는 postgres 이미지의 init 메커니즘(/docker-entrypoint-initdb.d/*.sql)으로
            // 컨테이너 최초 기동 시 "진짜 psql"이 실행한다. Spring의 spring.sql.init은 SQL을
            // 단순히 ';'로 분할해 PL/pgSQL 트리거 함수의 '$$ ... $$' 본문 안 ';'에서 잘못
            // 끊겨 'Unterminated dollar quote'로 실패하지만, psql은 달러쿼팅을 완벽히 처리한다.
            // Testcontainers는 매번 빈 데이터 디렉토리의 새 컨테이너라 initdb 스크립트가 항상 실행된다.
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("db/postgres/schema.sql"),
                    "/docker-entrypoint-initdb.d/01-schema.sql")
            // initdb 스크립트 실행 메커니즘은 postgres를 "임시 local-only"로 띄워
            // 스크립트를 돌린 뒤 TCP listener로 재시작한다. 즉 "ready to accept
            // connections" 로그가 두 번(임시 기동 + 최종 기동) 찍힌다. 첫 번째에서
            // 깨어나면 TCP가 아직 안 열려 connection refused가 난다. 그래서 그 메시지가
            // 두 번 찍힐 때까지 기다린다(=최종 TCP 기동 완료). amd64 이미지를 arm64에서
            // 에뮬레이션하면 schema+PostGIS 적재가 느리므로 startup timeout을 넉넉히 둔다.
            .waitingFor(
                    Wait.forLogMessage(".*database system is ready to accept connections.*", 2)
                            .withStartupTimeout(Duration.ofMinutes(5)));

    static {
        // 싱글톤: JVM 내내 한 번만 띄운다. stop()은 호출하지 않는다 —
        // JVM 종료 시 Testcontainers Ryuk 사이드카가 컨테이너를 자동 정리한다.
        POSTGRES.start();
    }

    /** 떠 있는 컨테이너의 접속 정보를 Spring DataSource 프로퍼티로 주입. */
    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }
}
