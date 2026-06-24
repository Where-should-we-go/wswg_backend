# PostGIS + pgvector — 공간 쿼리(ST_*)와 임베딩 유사도 검색(vector/<=>)을 함께 쓰기 위해
# 기본 postgis 이미지에 pgvector 확장을 추가한다. (postgis 이미지는 pgvector를 포함하지 않음)
FROM postgis/postgis:16-3.4

RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-16-pgvector \
    && rm -rf /var/lib/apt/lists/*
