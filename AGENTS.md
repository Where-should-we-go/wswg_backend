# AGENTS.md — WSWG Backend

> 이 문서는 **사람과 AI 도구(Claude Code / Codex / Gemini)가 공유하는 단일 작업 규칙**입니다.
> `CLAUDE.md`, `GEMINI.md`는 이 파일을 가리키기만 하며, 규칙의 원본은 항상 여기입니다.
> 규칙(린트로 검사 가능한 것)은 CI가 강제하고, 이 문서는 그 외의 맥락·판단을 담습니다.

## 프로젝트 한 줄 소개

"어디갈래?(WSWG, Where Should We Go)" — 지역·관광지 데이터 기반 여행 계획을 자동 생성하고 함께 편집하는 협업 여행 플래너의 **백엔드**.

- 제품 전체 방향·기능 정의: [README.md](./README.md)
- 데이터 모델/ERD: `src/main/resources/db/postgres/` (schema.sql, ERD.md, 데이터사전.md)

## 기술 스택

| 구분 | 내용 |
|---|---|
| 언어/런타임 | Java 21 |
| 프레임워크 | Spring Boot 3.5.x |
| 빌드 | Maven (`./mvnw` 래퍼) |
| DB | PostgreSQL + PostGIS (공간 쿼리) |
| 매퍼 | MyBatis |
| 인증 | Spring Security + OAuth2 Client + JWT (jjwt) |
| 캐시 | Redis (spring-data-redis) |
| 외부 연동 | 한국관광공사 TourAPI, OpenAI 임베딩 |
| 문서 | springdoc-openapi (Swagger UI) |
| 테스트 | JUnit5 + Testcontainers(PostgreSQL/PostGIS) |

## 명령어

```bash
./mvnw clean compile        # 빌드
./mvnw test                 # 테스트 (Docker 필요 — Testcontainers)
./mvnw -B verify            # CI와 동일: 컴파일 + 전체 테스트
./mvnw spring-boot:run      # 로컬 실행
./mvnw clean package        # 패키징
docker compose up -d        # 로컬 PostgreSQL 등 (compose.yaml)
```

> ⚠️ 테스트는 Testcontainers로 실제 PostgreSQL/PostGIS 컨테이너를 띄우므로 **Docker 데몬이 실행 중**이어야 합니다.

## 디렉토리 구조

```
src/main/java/com/ssafy/wswg/
├── WswgApplication.java     진입점
├── config/                  Security / WebMvc / OpenApi / Scheduling 설정
├── security/                OAuth2 · JWT 필터/핸들러
├── controller/              REST 컨트롤러
├── model/
│   ├── dao/                 MyBatis DAO 인터페이스
│   ├── dto/                 요청/응답/도메인 DTO
│   └── service/             비즈니스 로직
├── external/                외부 API (tour, openai)
├── interceptor/             LoggingInterceptor
├── exception/               CommonException · ErrorCode · GlobalExceptionHandler
└── util/                    JwtProvider 등
src/main/resources/
├── mapper/                  MyBatis XML
└── db/postgres/             schema.sql · ERD.md · 데이터사전.md
src/test/java/com/ssafy/wswg/ 테스트 코드
```

## 도메인 용어 (네이밍 통일)

| 한글 | 코드 네이밍 | 설명 |
|---|---|---|
| 관광지 | `Attraction` | 위경도·콘텐츠타입·이미지·설명 보유 |
| 시/도 | `Sido` (`sidoCode`) | 광역 지역 |
| 구/군 | `Gugun` (`gugunCode`) | 기초 지역, Sido에 속함 |
| 관광지 카테고리 | `ContentType` | 테마/유형 |
| 여행 그룹 | `Group` | 여행 계획 단위, 멤버 보유 |
| 사용자 | `User` (`Role`) | OAuth2 가입 |
| 수집 로그 | `BatchRunLog` | 외부 API 적재 이력 |

관계: `Sido 1:N Gugun`, `Sido 1:N Attraction`, `Gugun 1:N Attraction`

## Git 작업 방식

### 브랜치 전략: GitHub Flow
- `main`은 항상 배포 가능한 상태. **직접 push 금지** (PR + 리뷰 1명 이상 승인 후 merge).
- 기능마다 브랜치를 분기: `feat/검색-필터`, `fix/상세-502`, `refactor/적재-로직`

### Merge 전 최신 main에 rebase (필수)
merge 전에 브랜치를 **최신 main 위로 rebase**해 충돌을 미리 해소한다.
merge 자체는 **일반 merge(merge 커밋 유지)** 로 진행한다. (squash 안 함)

```bash
git fetch origin
git rebase origin/main          # 최신 main 위로 내 작업을 올림
# 충돌 해결 후
git push --force-with-lease      # rebase로 히스토리가 바뀌므로 force-with-lease (--force 금지)
```

- 브랜치 보호: **"Require branches to be up to date before merging"** 가 켜져 있어 최신이 아니면 merge 버튼이 막힘.
- **1 브랜치 = 1 작업자** 원칙. rebase + force-with-lease는 공유 브랜치에서 남의 커밋을 날릴 수 있으니, 한 브랜치를 둘이 같이 쓰지 않는다.

### 커밋 규칙
plain merge라 **모든 커밋이 그대로 main에 남는다.** 커밋 하나하나가 컨벤션을 지킬 것.

형식: `<type>(<scope>): <한글 요약>` (요약 50자 이내, 명령형, 마침표 없음)

| type | 용도 |
|---|---|
| feat | 새 기능 |
| fix | 버그 수정 |
| refactor | 동작 변화 없는 개선 |
| test | 테스트 추가/수정 |
| docs | 문서 |
| chore | 빌드·설정·의존성 |
| style | 포맷 등 동작 변화 없는 수정 |

예) `feat(search): 구·군 단독 검색 200 응답 추가`

### PR
- 제목도 커밋 컨벤션과 동일하게.
- 본문은 [.github/PULL_REQUEST_TEMPLATE.md](./.github/PULL_REQUEST_TEMPLATE.md) 양식을 채울 것 (**작성한 테스트** 섹션 필수).

### 최초 1회 로컬 설정 (온보딩)
```bash
git config pull.rebase true
git config branch.autoSetupRebase always
```

## 테스트: 테스트 먼저 작성 (TDD)
- 구현 전에 실패하는 테스트부터 작성 (Red → Green → Refactor).
- 새 로직이 있는 PR은 테스트를 동반한다.
- 위치: `src/test/java/com/ssafy/wswg/`, 프레임워크: JUnit5.
- DB 연동 테스트는 `AbstractPostgisIntegrationTest`를 상속 (Testcontainers).

## 하지 말 것
- `main`에 직접 push 금지.
- `.env`, 시크릿(JWT 키·OAuth secret·API 키) 커밋 금지 (`.gitignore` 확인).
- `git push --force` 금지 → `--force-with-lease`만.
- `**/db/postgres/reset.sql` 등 파괴적 스크립트 커밋 금지.
- 컨벤션을 어긴 커밋 메시지 금지.
