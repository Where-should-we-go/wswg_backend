# AI 후보 선택 기반 여행지 추천

## 목표

사용자의 자연어 여행 프롬프트를 바로 DB 검색어로만 쓰지 않고, 먼저 AI가 여러 여행 후보를 제안하게 한다. 사용자는 그 후보 중 마음에 드는 후보를 선택하고, 백엔드는 선택된 후보 텍스트를 embedding vector로 변환해 실제 DB 관광지와 매칭한다.

## 핵심 방향

```text
사용자 프롬프트
→ AI 후보 여행지 여러 개 생성
→ 후보 목록을 Redis 세션에 저장
→ 사용자/프론트가 후보 몇 개 선택
→ 백엔드가 선택 후보 텍스트를 embedding vector로 변환
→ attraction_embeddings와 cosine similarity 검색
→ 위치가 있으면 거리 점수 반영
→ 실제 DB 관광지 추천 반환
```

AI가 직접 1536차원 vector를 내려주지 않는다. 긴 vector JSON은 비용이 크고 깨지기 쉬우며, 일반 채팅 모델의 출력으로 신뢰하기 어렵다. AI는 후보의 이름/지역/설명/추천 이유를 만들고, 백엔드가 기존 `OpenAiEmbeddingClient`로 vector를 계산한다.

## 역할 분리

| 영역 | 역할 |
| --- | --- |
| AI | 후보 여행지 목록 생성, 자연어 안내 문구 생성 |
| Backend | 후보 세션 저장, embedding 생성, pgvector 유사도 검색, 거리 기반 점수 계산 |
| DB | `attraction_embeddings.embedding` 기반 cosine distance 검색 |
| Redis | 후보 세션과 선택 상태를 TTL로 임시 저장 |

## API 흐름

### 1. 후보 생성

```http
POST /api/ai/trip-candidates
```

요청:

```json
{
  "message": "부모님이랑 조용하게 다녀올 1박2일 여행지 추천해줘",
  "count": 8
}
```

응답:

```json
{
  "sessionId": "uuid",
  "reply": "조용하고 걷기 좋은 후보를 골라봤어요.",
  "candidates": [
    {
      "candidateId": "c1",
      "name": "전주 한옥마을",
      "regionHint": "전북 전주",
      "description": "전통 분위기와 산책 코스가 잘 어울리는 여행지",
      "reason": "부모님과 천천히 걷고 식사하기 좋습니다."
    }
  ],
  "nextQuestion": "이 중 마음에 드는 후보를 골라주세요."
}
```

### 2. 선택 후보 기반 추천

```http
POST /api/ai/trip-recommendations
```

요청:

```json
{
  "sessionId": "uuid",
  "selectedCandidateIds": ["c1", "c2"],
  "latitude": 37.5665,
  "longitude": 126.9780,
  "radiusMeters": 50000,
  "limit": 10
}
```

처리:

```text
Redis에서 후보 세션 조회
→ 선택된 후보 텍스트 구성
→ 후보별 embedding 생성
→ 후보별 pgvector top K 검색
→ contentId 기준 중복 제거
→ vectorSimilarity, distanceScore, regionMatch를 섞어 최종 점수 계산
```

응답:

```json
{
  "sessionId": "uuid",
  "reply": "선택한 취향과 가까운 실제 관광지를 정리했어요.",
  "recommendations": [
    {
      "contentId": 126508,
      "title": "전주 한옥마을",
      "similarity": 0.91,
      "distanceMeters": 17400,
      "score": 0.83,
      "matchedCandidateId": "c1",
      "matchedCandidateName": "전주 한옥마을"
    }
  ],
  "nextQuestion": "숙소와 식당까지 포함한 코스로 이어서 만들까요?"
}
```

### 3. 추천 기반 여행 계획 생성

```http
POST /api/ai/trip-plans
```

요청:

```json
{
  "title": "부모님과 전주 1박2일",
  "startDate": "2026-07-01",
  "endDate": "2026-07-02",
  "groupId": 10,
  "sessionId": "uuid",
  "selectedCandidateIds": ["c1", "c2"],
  "latitude": 37.5665,
  "longitude": 126.9780,
  "radiusMeters": 50000,
  "limit": 6
}
```

처리:

```text
추천 API와 동일한 방식으로 실제 관광지 추천 계산
→ 추천 결과를 trips.data.items 배열로 변환
→ 기존 TripService.createTrip으로 개인/그룹 여행 생성
→ 생성된 TripDto 반환
```

응답:

```json
{
  "tripId": 42,
  "title": "부모님과 전주 1박2일",
  "startDate": "2026-07-01",
  "endDate": "2026-07-02",
  "groupId": 10,
  "data": {
    "items": [
      {
        "id": "ai-place-1",
        "content_id": 126508,
        "contentId": 126508,
        "title": "전주 한옥마을",
        "contentTypeId": 12,
        "sidoCode": 35,
        "sidoName": "전북",
        "gugunCode": 35010,
        "gugunName": "전주시",
        "lat": 35.814,
        "lng": 127.153,
        "visitDate": "2026-07-01",
        "order": 1,
        "media": [],
        "properties": {
          "source": "AI_RECOMMENDATION",
          "score": 0.83,
          "similarity": 0.91,
          "matchedCandidateId": "c1",
          "matchedCandidateName": "전주 한옥마을"
        }
      }
    ],
    "aiRecommendation": {
      "sessionId": "uuid",
      "selectedCandidateIds": ["c1", "c2"],
      "createdAt": "2026-06-24T16:20:00+09:00"
    }
  }
}
```

`groupId`가 있으면 기존 여행 생성 규칙과 동일하게 그룹장만 생성할 수 있다. `groupId`가 없으면 로그인 사용자의 개인 여행으로 생성한다. `title`이 비어 있으면 기본값 `AI 추천 여행 계획`을 사용한다.

## 랭킹 기준

위치가 있을 때:

```text
score =
  vectorSimilarity * 0.70
+ distanceScore    * 0.20
+ regionMatch      * 0.10
```

위치가 없을 때:

```text
score =
  vectorSimilarity * 0.85
+ regionMatch      * 0.15
```

`distanceScore`는 가까울수록 1에 가깝고, `radiusMeters` 밖이면 0에 가깝게 본다. `regionMatch`는 AI 후보의 `regionHint`가 DB의 시도/구군/주소 텍스트에 포함될 때 가산한다.

## Redis 세션

키:

```text
ai:trip-candidate:{sessionId}
```

TTL:

```text
2시간
```

저장 내용:

```json
{
  "message": "...",
  "reply": "...",
  "candidates": [...],
  "createdAt": "2026-06-24T15:00:00+09:00"
}
```

## 주의점

- AI 후보는 실제 DB 관광지가 아닐 수 있다.
- 최종 추천은 반드시 `attractions`와 `attraction_embeddings`에 존재하는 관광지만 반환한다.
- 여행 계획 생성은 프론트가 내려준 장소를 그대로 믿지 않고, 선택 후보 기준으로 백엔드가 추천을 다시 계산한 결과만 `trips.data.items`에 저장한다.
- 후보 하나당 embedding을 만들고 top K를 조회한 뒤 합치면, 여러 취향을 선택했을 때 다양성이 더 좋다.
- AI 후보 JSON은 파싱 실패 가능성이 있으므로 응답 스키마를 엄격히 요구하고, 파싱 실패 시 서버 오류로 처리한다.
