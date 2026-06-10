CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(100),
    profile_image_url TEXT,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
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
    content_id INTEGER,
    title VARCHAR(255) NOT NULL,
    content_type_id INTEGER REFERENCES contenttypes(content_type_id),
    area_code INTEGER REFERENCES sidos(sido_code),
    si_gun_gu_code INTEGER,
    first_image1 TEXT,
    first_image2 TEXT,
    map_level INTEGER,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    geom geometry(Point, 4326),
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
