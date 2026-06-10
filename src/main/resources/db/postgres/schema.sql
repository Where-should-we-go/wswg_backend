CREATE EXTENSION IF NOT EXISTS postgis;

-- 공용 트리거 함수: UPDATE 시 updated_at 자동 갱신
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(100),
    profile_image_url TEXT,
    role VARCHAR(20) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER','ADMIN')),
    created_at DATE DEFAULT CURRENT_DATE
);

CREATE TABLE IF NOT EXISTS sidos (
    sido_code INTEGER PRIMARY KEY,
    sido_name VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS guguns (
    gugun_code INTEGER NOT NULL,
    sido_code INTEGER NOT NULL REFERENCES sidos(sido_code) ON DELETE CASCADE,
    gugun_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (sido_code, gugun_code)
);

CREATE TABLE IF NOT EXISTS contenttypes (
    content_type_id INTEGER PRIMARY KEY,
    content_type_name VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS attractions (
    no BIGSERIAL PRIMARY KEY,
    content_id INTEGER NOT NULL UNIQUE,   -- TourAPI 고유키, 자식 FK 타깃
    title VARCHAR(255) NOT NULL,
    content_type_id INTEGER REFERENCES contenttypes(content_type_id),
    -- 명칭 매핑: area_code = sido_code, si_gun_gu_code = gugun_code (TourAPI 원문 유지)
    area_code INTEGER REFERENCES sidos(sido_code),
    si_gun_gu_code INTEGER,
    first_image1 TEXT,
    first_image2 TEXT,
    map_level INTEGER,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    -- geom: lat/lng로부터 자동 생성 (3NF: 좌표 단일 출처, 동기화 불필요)
    geom geometry(Point, 4326) GENERATED ALWAYS AS
        (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)) STORED,
    tel VARCHAR(100),
    addr1 TEXT,
    addr2 TEXT,
    homepage TEXT,
    overview TEXT,
    CONSTRAINT fk_attractions_guguns
        FOREIGN KEY (area_code, si_gun_gu_code)
        REFERENCES guguns(sido_code, gugun_code)
);

CREATE INDEX IF NOT EXISTS idx_attractions_region
    ON attractions(area_code, si_gun_gu_code);

CREATE INDEX IF NOT EXISTS idx_attractions_content_type
    ON attractions(content_type_id);

CREATE INDEX IF NOT EXISTS idx_attractions_title
    ON attractions(title);

CREATE INDEX IF NOT EXISTS idx_attractions_geom
    ON attractions USING GIST(geom);

-- ============================================================
-- 여행/모임/미디어 도메인 (추가)
-- ============================================================

-- (attractions.content_id 는 위 정의에서 NOT NULL UNIQUE → 자식 테이블 FK 타깃으로 사용)

-- 모임
CREATE TABLE IF NOT EXISTS groups (
    group_id   BIGSERIAL PRIMARY KEY,
    group_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 모임 멤버 매핑
CREATE TABLE IF NOT EXISTS user_group (
    user_group_id BIGSERIAL PRIMARY KEY,
    user_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    group_id  BIGINT NOT NULL REFERENCES groups(group_id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, group_id)
);
CREATE INDEX IF NOT EXISTS idx_user_group_group ON user_group(group_id);

-- 여행 (개인 user_id / 모임 group_id 중 정확히 하나 = XOR)
CREATE TABLE IF NOT EXISTS trips (
    trip_id    BIGSERIAL PRIMARY KEY,
    title      VARCHAR(255) NOT NULL,
    start_date DATE,
    end_date   DATE,
    user_id    BIGINT REFERENCES users(id) ON DELETE CASCADE,
    group_id   BIGINT REFERENCES groups(group_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 마이페이지 '최근 수정' 정렬용
    CONSTRAINT chk_trips_owner_xor
        CHECK ((user_id IS NULL) <> (group_id IS NULL)),
    CONSTRAINT chk_trips_dates
        CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date)
);
CREATE INDEX IF NOT EXISTS idx_trips_user  ON trips(user_id);
CREATE INDEX IF NOT EXISTS idx_trips_group ON trips(group_id);

-- updated_at 자동 갱신 트리거
DROP TRIGGER IF EXISTS trg_trips_updated_at ON trips;
CREATE TRIGGER trg_trips_updated_at
    BEFORE UPDATE ON trips
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 여행 항목 (유연한 데이터 단위 — 노션식 자유 필드, 일정/지도 등 여러 뷰의 원본)
-- 관광지(content_id)일 수도, 맛집·이동·메모 같은 자유 항목일 수도 있음
CREATE TABLE IF NOT EXISTS trip_items (
    item_id     BIGSERIAL PRIMARY KEY,
    trip_id     BIGINT  NOT NULL REFERENCES trips(trip_id) ON DELETE CASCADE,
    content_id  INTEGER REFERENCES attractions(content_id),   -- TourAPI 관광지면 연결, 아니면 NULL
    title       VARCHAR(255),                                 -- 직접 입력(맛집/메모 등). content_id 있으면 생략 가능
    item_type   VARCHAR(30),                                  -- 관광/식당/숙박/이동/메모… (자유)
    latitude    DOUBLE PRECISION,                             -- 자체 위치(관광지 아닐 때)
    longitude   DOUBLE PRECISION,
    geom geometry(Point, 4326) GENERATED ALWAYS AS
        (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)) STORED,
    visit_order INTEGER,
    visit_date  DATE,
    visit_time  TIME,
    properties  JSONB NOT NULL DEFAULT '{}'::jsonb,           -- 🌟 노션식 자유 필드(예산/평점/태그/메모…)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 관광지 링크가 없으면 최소한 직접 입력 제목은 있어야 함
    CONSTRAINT chk_trip_item_ref CHECK (content_id IS NOT NULL OR title IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_trip_items_trip      ON trip_items(trip_id);
CREATE INDEX IF NOT EXISTS idx_trip_items_content   ON trip_items(content_id);
CREATE INDEX IF NOT EXISTS idx_trip_items_props_gin ON trip_items USING GIN  (properties);
CREATE INDEX IF NOT EXISTS idx_trip_items_geom_gist ON trip_items USING GIST (geom);

DROP TRIGGER IF EXISTS trg_trip_items_updated_at ON trip_items;
CREATE TRIGGER trg_trip_items_updated_at
    BEFORE UPDATE ON trip_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 여행 권역 집계 스냅샷 (발자취 지도 색칠 소스)
-- ⚠️ 의도적 비정규화(읽기 최적화 캐시): attraction_count는 앱 서비스 로직에서 갱신
CREATE TABLE IF NOT EXISTS trip_region_snapshots (
    snapshot_id      BIGSERIAL PRIMARY KEY,
    trip_id          BIGINT  NOT NULL REFERENCES trips(trip_id) ON DELETE CASCADE,
    sido_code        INTEGER NOT NULL REFERENCES sidos(sido_code),
    gugun_code       INTEGER,                       -- NULL = 구군 미상 허용
    attraction_count INTEGER NOT NULL DEFAULT 0
);
-- (trip, sido, gugun) 중복 방지 — NULL도 -1로 치환해 유일성 보장
CREATE UNIQUE INDEX IF NOT EXISTS uq_snapshot_region
    ON trip_region_snapshots (trip_id, sido_code, COALESCE(gugun_code, -1));

-- 멀티미디어 (사진/음성/영상) — metadata 만 JSONB 하이브리드
CREATE TABLE IF NOT EXISTS trip_media (
    media_id   BIGSERIAL PRIMARY KEY,
    trip_id    BIGINT  NOT NULL REFERENCES trips(trip_id) ON DELETE CASCADE,
    item_id    BIGINT  REFERENCES trip_items(item_id) ON DELETE SET NULL,  -- 어느 항목(장소)에 붙은 미디어 (없으면 여행 전체)
    user_id    BIGINT  REFERENCES users(id) ON DELETE SET NULL,            -- 업로더 탈퇴해도 미디어 보존
    media_type VARCHAR(10)  NOT NULL CHECK (media_type IN ('PHOTO','AUDIO','VIDEO')),
    media_url  TEXT NOT NULL,
    geom       geometry(Point, 4326),                            -- 촬영 좌표(직접 입력)
    metadata   JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_trip_media_trip     ON trip_media(trip_id);
CREATE INDEX IF NOT EXISTS idx_trip_media_item     ON trip_media(item_id);
CREATE INDEX IF NOT EXISTS idx_trip_media_user     ON trip_media(user_id);
CREATE INDEX IF NOT EXISTS idx_trip_media_meta_gin ON trip_media USING GIN  (metadata);
CREATE INDEX IF NOT EXISTS idx_trip_media_geom_gist ON trip_media USING GIST (geom);
