# MySQL to PostgreSQL/PostGIS Migration

## 1. Start PostgreSQL/PostGIS

```bash
docker compose up -d postgres redis
```

The PostGIS schema is initialized from:

```text
src/main/resources/db/postgres/schema.sql
```

## 2. Environment

```bash
export MYSQL_URL="mysql://ssafy:ssafy@localhost:3306/ssafy_trip"
export POSTGRES_URL="postgresql://wswg:wswg@localhost:55432/wswg"
```

## 3. Migrate Data With pgloader

Install pgloader if it is not available:

```bash
brew install pgloader
```

Run the migration:

```bash
pgloader "$MYSQL_URL" "$POSTGRES_URL"
```

## 4. PostGIS Geometry Backfill

After migration, populate the PostGIS point column from existing longitude/latitude values:

```sql
UPDATE attractions
SET geom = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)
WHERE longitude IS NOT NULL
  AND latitude IS NOT NULL;
```

## 5. Validation

```sql
SELECT COUNT(*) FROM users;
SELECT COUNT(*) FROM sidos;
SELECT COUNT(*) FROM guguns;
SELECT COUNT(*) FROM contenttypes;
SELECT COUNT(*) FROM attractions;

SELECT PostGIS_Version();

SELECT COUNT(*) AS attractions_with_geom
FROM attractions
WHERE geom IS NOT NULL;
```

## 6. Application Datasource

Spring Boot now uses PostgreSQL:

```yaml
spring:
  datasource:
    driver-class-name: org.postgresql.Driver
    url: ${POSTGRES_URL:jdbc:postgresql://localhost:55432/wswg}
    username: ${POSTGRES_USER:wswg}
    password: ${POSTGRES_PASSWORD:wswg}
```
