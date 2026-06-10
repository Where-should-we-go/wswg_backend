# DB 스키마 적용 가이드 (PostgreSQL)

대상 DB: `jdbc:postgresql://localhost:55432/wswg` (application.yml 기준)
파일:
- `schema.sql` — 전체 스키마 (11 테이블 + PostGIS + 트리거)
- `reset.sql` — 초기화(DROP) — 개발용

> ⚠️ `schema.sql`은 `CREATE TABLE IF NOT EXISTS` 라서 **이미 만들어진 테이블에는 변경이 반영되지 않습니다.**
> attractions의 `geom GENERATED`·`content_id NOT NULL UNIQUE` 같은 변경을 적용하려면 **fresh 재생성**이 필요합니다.

---

## 1) 사전 점검 — content_id 중복/NULL (기존 데이터가 있을 때만)

`content_id`를 `NOT NULL UNIQUE`로 바꾸므로, 기존 데이터가 있으면 먼저 확인:
```sql
-- NULL 존재?
SELECT count(*) FROM attractions WHERE content_id IS NULL;
-- 중복 존재?
SELECT content_id, count(*) FROM attractions
GROUP BY content_id HAVING count(*) > 1;
```
둘 다 0이어야 적용 성공. (아직 TourAPI 적재 전이면 보통 비어 있음)

## 2) 적용 (fresh 재생성 — 개발 환경)

```bash
cd backend/src/main/resources/db/postgres

# (1) 초기화  ⚠️ 데이터 전부 삭제
psql -h localhost -p 55432 -U wswg -d wswg -f reset.sql

# (2) 스키마 생성
psql -h localhost -p 55432 -U wswg -d wswg -f schema.sql
```
비밀번호 프롬프트가 뜨면 `wswg` (또는 .env의 POSTGRES_PASSWORD).
한 줄로: `PGPASSWORD=wswg psql -h localhost -p 55432 -U wswg -d wswg -f schema.sql`

## 3) 검증

```sql
-- 테이블 11개 확인
\dt

-- 생성 컬럼(geom) 동작 확인
INSERT INTO sidos VALUES (1,'서울');
INSERT INTO guguns VALUES (1,1,'종로구');
INSERT INTO contenttypes VALUES (12,'관광지');
INSERT INTO attractions (content_id,title,content_type_id,area_code,si_gun_gu_code,latitude,longitude)
VALUES (126508,'경복궁',12,1,1,37.5796,126.9770);
SELECT content_id, ST_AsText(geom) FROM attractions WHERE content_id=126508;
-- → POINT(126.977 37.5796) 자동 생성되면 성공
```

## 4) Docker로 PostGIS 띄우기 (psql/DB 없을 때 참고)

```bash
docker run -d --name wswg-pg -p 55432:5432 \
  -e POSTGRES_USER=wswg -e POSTGRES_PASSWORD=wswg -e POSTGRES_DB=wswg \
  postgis/postgis:16-3.4
```

---

## 향후 권장 — Flyway 도입
지금은 수동 적용이라 협업 시 실수 위험. `flyway-core` 의존성 + `schema.sql`을
`db/migration/V1__init.sql` 로 옮기면 **앱 기동 시 자동·버전관리 적용**됩니다. (이슈 B-2 후속)
