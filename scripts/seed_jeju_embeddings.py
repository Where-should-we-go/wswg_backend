#!/usr/bin/env python3
"""제주(sido_code=50) + 이미지 보유 관광지의 임베딩 시드 로더 (일회성).

attraction_embeddings 가 비어 있어 AI 추천/의미검색이 결과를 못 내는 문제를 풀기 위한
소량 시드. 관광지 텍스트(제목+지역+주소)를 text-embedding-3-small 로 임베딩해 적재한다.
- 임베딩 API: GMS 프록시(OpenAI). 키는 backend/.env 의 OPENAI_API_KEY.
- DB 접근: 실행 중 docker 컨테이너 wswg-postgres 의 psql.
- 재실행 안전: ON CONFLICT(content_id, embedding_model) DO NOTHING.
"""
import json
import subprocess
import sys
import time
import urllib.request

EMBED_URL = "https://gms.ssafy.io/gmsapi/api.openai.com/v1/embeddings"
MODEL = "text-embedding-3-small"
BATCH = 32
PG = ["docker", "exec", "-i", "wswg-postgres", "psql", "-U", "wswg", "-d", "wswg", "-v", "ON_ERROR_STOP=1"]


def read_key():
    with open("backend/.env" if __import__("os").path.exists("backend/.env") else ".env", encoding="utf-8") as f:
        for line in f:
            if line.startswith("OPENAI_API_KEY="):
                return line.split("=", 1)[1].strip().strip('"').strip("'")
    sys.exit("OPENAI_API_KEY not found in .env")


def fetch_rows():
    sql = (
        "SELECT coalesce(json_agg(json_build_object("
        "'id', a.content_id, "
        "'t', concat_ws(' ', a.title, s.sido_name, g.gugun_name, a.addr1))), '[]') "
        "FROM attractions a "
        "LEFT JOIN sidos s ON s.sido_code = a.sido_code "
        "LEFT JOIN guguns g ON g.sido_code = a.sido_code AND g.gugun_code = a.gugun_code "
        "LEFT JOIN attraction_embeddings e ON e.content_id = a.content_id AND e.embedding_model = '" + MODEL + "' "
        "WHERE a.sido_code = 50 AND a.first_image1 IS NOT NULL AND a.first_image1 <> '' "
        "AND e.content_id IS NULL;"  # 아직 임베딩 없는 것만.
    )
    out = subprocess.run(PG + ["-t", "-A", "-c", sql], capture_output=True, text=True, check=True)
    return json.loads(out.stdout.strip() or "[]")


def embed(key, texts):
    # urllib 의 http.client 는 GMS 프록시의 청크 응답에서 IncompleteRead 로 자주 끊긴다.
    # 단건/배치 모두 curl 이 안정적이라 curl 로 호출한다(--compressed, 재시도).
    body = json.dumps({"model": MODEL, "input": texts})
    last = None
    for attempt in range(5):
        try:
            proc = subprocess.run(
                ["curl", "-sS", "--compressed", "--max-time", "120", "-X", "POST", EMBED_URL,
                 "-H", "Authorization: Bearer " + key, "-H", "Content-Type: application/json",
                 "--data-binary", "@-"],
                input=body, capture_output=True, text=True, check=True)
            data = json.loads(proc.stdout)
            if "data" not in data:
                raise RuntimeError("embed error: " + proc.stdout[:200])
            items = sorted(data["data"], key=lambda d: d["index"])  # 입력 순서 보장.
            return [it["embedding"] for it in items]
        except Exception as e:  # noqa: BLE001 — 일시적 절단/타임아웃 재시도.
            last = e
            time.sleep(1.5 * (attempt + 1))
    raise last


def sql_escape(s):
    return s.replace("'", "''")


def insert_batch(rows, vectors):
    values = []
    for r, vec in zip(rows, vectors):
        lit = "[" + ",".join(repr(float(x)) for x in vec) + "]"
        values.append(
            "(%d, '%s'::vector, '%s', '%s')" % (r["id"], lit, sql_escape(r["t"]), MODEL))
    stmt = (
        "INSERT INTO attraction_embeddings (content_id, embedding, embedding_text, embedding_model) VALUES "
        + ",".join(values)
        + " ON CONFLICT (content_id, embedding_model) DO NOTHING;"
    )
    # -c 인자는 길이 한계(ARG_MAX)에 걸린다. SQL 은 stdin 으로 파이프.
    subprocess.run(PG + ["-q"], input=stmt, capture_output=True, text=True, check=True)


def main():
    key = read_key()
    rows = fetch_rows()
    total = len(rows)
    print(f"적재 대상(미적재) {total}건")
    done = 0
    for i in range(0, total, BATCH):
        chunk = rows[i:i + BATCH]
        vectors = embed(key, [r["t"] for r in chunk])
        insert_batch(chunk, vectors)
        done += len(chunk)
        print(f"  {done}/{total}", flush=True)
    print("완료")


if __name__ == "__main__":
    main()
