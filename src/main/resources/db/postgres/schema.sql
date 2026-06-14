CREATE EXTENSION IF NOT EXISTS postgis;

-- 공용 트리거 함수: UPDATE 시 updated_at 자동 갱신
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- 회원 / 관광 마스터 (TourAPI 배치 적재)
-- ============================================================

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
    content_id INTEGER NOT NULL UNIQUE,   -- TourAPI 고유키, trips.data가 소프트 참조
    title VARCHAR(255) NOT NULL,
    content_type_id INTEGER REFERENCES contenttypes(content_type_id),
    sido_code INTEGER REFERENCES sidos(sido_code),   -- 법정동 시도코드(TourAPI lDongRegnCd; 11=서울)
    gugun_code INTEGER,                              -- 법정동 시군구코드(lDongSignguCd; 110=종로구)
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
        FOREIGN KEY (sido_code, gugun_code)
        REFERENCES guguns(sido_code, gugun_code)
);
CREATE INDEX IF NOT EXISTS idx_attractions_region       ON attractions(sido_code, gugun_code);
CREATE INDEX IF NOT EXISTS idx_attractions_content_type ON attractions(content_type_id);
CREATE INDEX IF NOT EXISTS idx_attractions_title        ON attractions(title);
CREATE INDEX IF NOT EXISTS idx_attractions_geom         ON attractions USING GIST(geom);

-- ============================================================
-- 모임
-- ============================================================

CREATE TABLE IF NOT EXISTS groups (
    group_id   BIGSERIAL PRIMARY KEY,
    group_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS user_group (
    user_group_id BIGSERIAL PRIMARY KEY,
    user_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    group_id  BIGINT NOT NULL REFERENCES groups(group_id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, group_id)
);
CREATE INDEX IF NOT EXISTS idx_user_group_group ON user_group(group_id);

-- ============================================================
-- 여행 (계획↔기록) — 항목/미디어 전체를 data JSONB 문서로 보관
--   data 예: { "items": [ { "content_id":126508, "title":"경복궁", "type":"관광",
--                           "lat":37.5, "lng":126.9, "visitDate":"2026-07-01", "order":1,
--                           "media":[{"type":"PHOTO","url":"..."}],
--                           "properties":{"budget":0,"memo":"..."} }, ... ] }
-- ============================================================

CREATE TABLE IF NOT EXISTS trips (
    trip_id    BIGSERIAL PRIMARY KEY,
    title      VARCHAR(255) NOT NULL,
    start_date DATE,
    end_date   DATE,                                  -- 완료 판정: end_date < 오늘
    user_id    BIGINT REFERENCES users(id) ON DELETE CASCADE,
    group_id   BIGINT REFERENCES groups(group_id) ON DELETE CASCADE,
    data       JSONB NOT NULL DEFAULT '{}'::jsonb,    -- 🌟 항목(+미디어) 전체 문서
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_trips_owner_xor
        CHECK ((user_id IS NULL) <> (group_id IS NULL)),
    CONSTRAINT chk_trips_dates
        CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date)
);
CREATE INDEX IF NOT EXISTS idx_trips_user     ON trips(user_id);
CREATE INDEX IF NOT EXISTS idx_trips_group    ON trips(group_id);
CREATE INDEX IF NOT EXISTS idx_trips_data_gin ON trips USING GIN (data);   -- data 내부 검색

DROP TRIGGER IF EXISTS trg_trips_updated_at ON trips;
CREATE TRIGGER trg_trips_updated_at
    BEFORE UPDATE ON trips
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================
-- 그룹 지도: 지역당 대표 추억 미디어 1개 (trips.data 미디어 중 선택해 복사)
-- ============================================================

CREATE TABLE IF NOT EXISTS group_region_media (
    id          BIGSERIAL PRIMARY KEY,
    group_id    BIGINT  NOT NULL REFERENCES groups(group_id) ON DELETE CASCADE,  -- 어느 그룹 지도
    trip_id     BIGINT  REFERENCES trips(trip_id) ON DELETE SET NULL,            -- 출처 여행(선택)
    sido_code   INTEGER NOT NULL REFERENCES sidos(sido_code),
    gugun_code  INTEGER,                                                         -- NULL = 구군 미상
    media_type  VARCHAR(10) NOT NULL CHECK (media_type IN ('PHOTO','AUDIO','VIDEO')),
    media_url   TEXT NOT NULL,                                                   -- 선택된 대표 미디어 url(복사)
    geom        geometry(Point, 4326),                                           -- 핀 좌표(선택)
    metadata    JSONB NOT NULL DEFAULT '{}'::jsonb,                              -- 썸네일/메타(선택)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- 지역당 1개 (NULL 구군도 -1로 치환해 유일성 보장)
CREATE UNIQUE INDEX IF NOT EXISTS uq_group_region_media
    ON group_region_media (group_id, sido_code, COALESCE(gugun_code, -1));
CREATE INDEX IF NOT EXISTS idx_grm_group     ON group_region_media(group_id);
CREATE INDEX IF NOT EXISTS idx_grm_geom_gist ON group_region_media USING GIST (geom);
