# DB 스키마 가이드 (PostgreSQL)

대상 DB: `jdbc:postgresql://localhost:55432/wswg` (application.yml 기준)

**파일**
- `schema.sql` — 전체 스키마 (9 테이블 + PostGIS + 트리거 + contenttypes 8종 시드)
- `ERD.md` — ERD 다이어그램·관계·확정 규칙
- `데이터사전.md` — 필드 단위 데이터 사전
- `여행기록도메인.md` — trips.data·group_region_media 시나리오 정리

> ⚠️ `schema.sql`은 `CREATE TABLE IF NOT EXISTS` 라서 **이미 만들어진 테이블에는 변경이 반영되지 않습니다.**
> `geom GENERATED`·`content_id NOT NULL UNIQUE` 같은 변경은 **fresh 재생성**이 필요합니다.
>
> 💡 파괴적 초기화 스크립트(reset)는 **레포에 커밋하지 않습니다**(footgun 방지). 필요하면 아래 §2의 DROP 스니펫을 로컬에서 직접 실행하세요.

---

## 1) 사전 점검 — content_id 중복/NULL (기존 데이터가 있을 때만)

`content_id`를 `NOT NULL UNIQUE`로 바꾸므로, 기존 데이터가 있으면 먼저 확인:
```sql
SELECT count(*) FROM attractions WHERE content_id IS NULL;          -- NULL 존재?
SELECT content_id, count(*) FROM attractions
GROUP BY content_id HAVING count(*) > 1;                            -- 중복 존재?
```
둘 다 0이어야 적용 성공. (TourAPI 적재 전이면 보통 비어 있음)

## 2) 적용 (fresh 재생성 — 개발 환경 전용)

```bash
cd backend/src/main/resources/db/postgres
PGPASSWORD=wswg psql -h localhost -p 55432 -U wswg -d wswg -f schema.sql
```

이미 테이블이 있어 **갈아엎어야 할 때** (⚠️ 개발 DB에서만, 데이터 전부 삭제):
```sql
-- 로컬에서 직접 실행 (운영 DB 금지)
DROP TABLE IF EXISTS
    group_region_media, trips,
    user_group, groups, attractions, contenttypes, guguns, sidos, users CASCADE;
DROP FUNCTION IF EXISTS set_updated_at() CASCADE;
```
→ 그 후 다시 `schema.sql` 실행.

## 3) 검증

```sql
\dt   -- 테이블 9개 확인

-- 생성 컬럼(geom) 자동 생성 확인
INSERT INTO sidos VALUES (1,'서울');
INSERT INTO guguns VALUES (1,1,'종로구');
INSERT INTO contenttypes VALUES (12,'관광지');
INSERT INTO attractions (content_id,title,content_type_id,sido_code,gugun_code,latitude,longitude)
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
지금은 수동 적용이라 협업 시 실수 위험. `flyway-core` + `schema.sql`을
`db/migration/V1__init.sql` 로 옮기면 **앱 기동 시 자동·버전관리 적용**, 운영 초기화는 `cleanDisabled=true`로 차단됩니다. (이슈 B-2 후속)
