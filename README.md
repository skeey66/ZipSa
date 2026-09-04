# ZIP 보금자리 (ZipSa)

청년 주거 정책 · 공공임대 · 실거래가 · 대출을 한 곳에서 확인하는 서비스입니다.
흩어져 있는 공공 데이터를 모아서, **내 조건에 맞는 것만** 골라 보여줍니다.

> **범위 원칙: 와이어프레임에 그려진 화면이 유일한 기준입니다.**
> 화면에 없는 기능은 만들지 않습니다. 기능을 추가하려면 와이어프레임을 먼저 그리세요.

---

## 구성

```
Vue 3 SPA  →  Spring Boot (+ Spring AI)  →  PostgreSQL 17
                        ↓                        ↑
                     OpenAI            Python 크롤러 (직접 write)
                                              ↑
                            공공 API · 언론사 RSS
```

| 디렉터리 | 내용 | 스택 |
| --- | --- | --- |
| `frontend/` | 웹 클라이언트 | Vue 3 · Vite · Pinia · 카카오맵 |
| `backend/` | API 서버 (유일한 외부 진입점) | Spring Boot 3.5 · Java 21 · Spring AI |
| `crawler/` | 정책·공고·실거래가·뉴스 수집 배치 | Python 3.11 · psycopg |
| `docs/` | API 명세 · DB 스키마 · 설계 문서 | — |
| `scripts/` | 더미 데이터 · 키 점검 · 문서 생성 | Python |

**백엔드가 유일한 외부 진입점입니다.** 프론트는 DB 를 모르고, 크롤러는 API 를 거치지 않고 DB 에 직접 씁니다.
(예외: 카카오 지도 SDK 만 브라우저가 직접 부릅니다.)

---

## 기능

| 화면 | 하는 일 |
| --- | --- |
| 홈 | 진입점. 정책·매물·커뮤니티로 분기 |
| 로그인 · 회원가입 | JWT 인증. 가입 시 나이대·혼인·직업·소득·지역 온보딩 |
| 마이페이지 | 프로필 조회·수정, 탈퇴 |
| 정책 리스트 · 상세 | 온통청년 정책. **내 프로필로 맞춤 추천**, AI 요약과 「나에게 어떻게 적용되나」 |
| 공공임대 | LH 모집공고와 마이홈 단지. 모집기간 캘린더 |
| 실거래가 | 국토부 실거래가를 카카오맵에 마커로 |
| 뉴스 | 언론사 RSS 6곳의 주거 기사, AI 요약 |
| 커뮤니티 | 게시글·댓글·추천. 은행별 대출 후기 뱃지 |
| 대출예측 · 결과입력 | 내 조건으로 한도 예측, 실제 승인 결과 축적 |

REST 엔드포인트 37개 · 테이블 17개 · Flyway 마이그레이션 21개.

### AI 는 보조 기능입니다

`OPENAI_API_KEY` 가 있으면 LLM 이 요약을 쓰고(「AI」 배지), **없거나 호출이 실패하면 규칙 기반으로 떨어집니다**(「샘플」 배지).
키가 없어도 서비스 전체가 정상 동작합니다. 숫자와 판정은 LLM 에 맡기지 않고 코드가 먼저 확정합니다.

---

## 실행 방법 두 가지

| | 언제 쓰나 | 명령 |
| --- | --- | --- |
| **A. Docker 한 방** | 처음 받았을 때 · 데모 | `docker compose up --build` |
| **B. 로컬 실행** | **개발할 때** — 코드 고치면 바로 반영 | 터미널 2개 (아래) |

> 개발 중에는 **B** 를 쓰세요. A 는 코드를 고칠 때마다 이미지를 다시 빌드해야 해서 느립니다.

### 공통 준비 (최초 1회)

Java 21 · Node 20+ · Python 3.11+ · Docker (A 방식) 또는 PostgreSQL 17 (B 방식)

```bash
cp .env.example .env
openssl rand -base64 48      # 출력값을 .env 의 JWT_SECRET 에 붙여넣기
```

`.env` 의 `POSTGRES_PASSWORD` 도 아무 값으로 채웁니다. **`.env` 는 절대 커밋하지 않습니다.**
`OPENAI_API_KEY` · `DATA_GO_KR_SERVICE_KEY` · `KAKAO_REST_API_KEY` · `VITE_KAKAO_MAP_KEY` 는 없으면
해당 기능만 비활성화되고 나머지는 그대로 돕니다.

---

## A. Docker 로 한 번에 실행

```bash
docker compose up --build
```

| 주소 | 내용 |
| --- | --- |
| **http://localhost:3000** | 웹 화면 (여기로 들어가세요) |
| http://localhost:3000/swagger-ui.html | API 문서 |
| `localhost:5433` | DB (DBeaver 등으로 접속. 로컬 5432 와 충돌 방지) |

- 스키마는 백엔드가 뜨면서 **Flyway 가 자동 적용**합니다.
- 프론트는 nginx 가 서빙하고 `/api` 를 백엔드로 넘기므로 **CORS 설정이 필요 없습니다.**
- 끄기: `Ctrl+C` 또는 `docker compose down`
- **DB 까지 초기화**: `docker compose down -v` (볼륨 삭제 — 데이터 전부 사라짐)
- `VITE_KAKAO_MAP_KEY` 는 **빌드 시점에** 번들로 들어갑니다. 키를 바꿨으면 `docker compose up --build frontend`

### 크롤러 (배치)

상주 서비스가 아니라 `up` 으로는 뜨지 않습니다.

```bash
docker compose run --rm crawler --target policy
```

---

## B. 로컬에서 실행 (개발용)

### 1. DB 만 컨테이너로

```bash
docker compose up -d db
```

로컬에 PostgreSQL 이 이미 있다면 DB 와 롤만 만들어도 됩니다.

```sql
CREATE ROLE zipsa LOGIN PASSWORD '...';
CREATE DATABASE zipsa OWNER zipsa;
```

### 2. 터미널 1 — 백엔드

```bash
cd backend
set -a && . ../.env && set +a     # Spring Boot 는 .env 를 자동으로 안 읽습니다. 빼먹으면 기동 실패
./gradlew bootRun
```

`Started ZipsaApplication` 이 뜨면 성공. → http://localhost:8080

### 3. 터미널 2 — 프론트엔드

```bash
cd frontend
npm install      # 최초 1회
npm run dev
```

→ **http://localhost:5173** (Vite 프록시가 `/api` 를 백엔드로 넘깁니다)

### 4. 크롤러

```bash
cd crawler
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
```

먼저 키가 제대로 들어갔는지 확인하세요. 인코딩/디코딩 실수까지 잡아줍니다.

```bash
crawler/.venv/bin/python scripts/check_keys.py
```

```bash
python -m zipsa_crawler.main --target policy           # 온통청년 정책
python -m zipsa_crawler.main --target public-housing   # LH 공고 · 마이홈 단지
python -m zipsa_crawler.main --target news             # 언론사 RSS 6곳
python -m zipsa_crawler.main --target transaction      # 실거래가 (서울 25구 × 3개월, 10분 남짓)

# 한 지역만 빠르게 확인
python -m zipsa_crawler.main --target transaction --region 11200 --months 1
```

실행 한 번이 `crawl_jobs` 한 행입니다. 중간에 실패해도 어디까지 갔는지 남습니다.

---

## 동작 확인

회원가입 → 로그인 → 마이페이지에 본인 닉네임과 `20대 후반 · 미혼 · 직장인` 태그가 뜨면
**프론트 ↔ 백엔드 ↔ DB 가 전부 연결된 것입니다.**

지도가 안 보이면 **카카오 개발자 콘솔에 도메인을 등록**해야 합니다.

> developers.kakao.com → 내 애플리케이션 → 앱 설정 → **플랫폼 → Web → 사이트 도메인**
> `http://localhost:5173`(로컬) 과 `http://localhost:3000`(Docker) 을 **둘 다** 등록

---

## 개발용 더미 데이터

정책·회원·조회이력이 없으면 목록도 추천도 확인할 수 없어서 시드 생성기를 넣어뒀습니다.

```bash
crawler/.venv/bin/python scripts/seed_dev_data.py          # 생성
crawler/.venv/bin/python scripts/seed_dev_data.py --reset  # 지우고 다시
crawler/.venv/bin/python scripts/seed_dev_data.py --clean  # 삭제만
```

| 항목 | 수량 |
| --- | --- |
| 정책 | 36 |
| 회원 | 160 (프로필 군집 4개) |
| 정책 조회로그 | ~2,900 |
| 정책 찜 | ~610 |
| 게시글 · 댓글 · 좋아요 | 72 · 245 · 879 |

- 더미 계정: `seed_user_001` ~ `seed_user_160` / 비밀번호 `Demo!pass12`
- **시드 고정**이라 누가 돌려도 같은 데이터가 나옵니다. 삭제는 `seed_user_%` / `SEED-POLICY-%` 행만 지우므로 직접 가입한 계정은 남습니다.
- 조회로그는 **군집별로 선호 패턴을 심어놨습니다.** 균등 난수면 추천이 그냥 인기순이 돼서 로직 검증이 안 됩니다.

심어둔 패턴이 복원되는지 확인:

```bash
psql -h localhost -U zipsa -d zipsa -f scripts/verify_seed.sql
```

> Docker 스택(5433)에 넣으려면 `POSTGRES_PORT=5433 crawler/.venv/bin/python scripts/seed_dev_data.py`

---

## 협업 방식

브랜치는 셋뿐입니다. 각자 자기 브랜치에서 작업하고 **마지막에 `main` 에서 합칩니다.**

| 브랜치 | 누가 |
| --- | --- |
| `main` | 합치는 곳. 직접 push 하지 않습니다 |
| `backend` | 백엔드 담당 |
| `frontend` | 프론트 담당 |

명령어와 담당 파일 목록은 아래 문서에 있습니다.

| 문서 | 내용 |
| --- | --- |
| [docs/collab/브랜치-작업법.md](docs/collab/브랜치-작업법.md) | **각자 복붙할 명령어** — remote 연결부터 push 까지 |
| [docs/collab/협업-시나리오.md](docs/collab/협업-시나리오.md) | 누가 무엇을 올리는지, 합치는 순서 |
| [docs/collab/manifest/](docs/collab/manifest/) | 담당별 파일 목록 (`push-BE1.txt` 등) |

---

## 문서

| 문서 | 내용 |
| --- | --- |
| [CONTRIBUTING.md](CONTRIBUTING.md) | 브랜치·커밋·PR·코드리뷰 규칙 (**작업 전 필독**) |
| [docs/DB.dbml](docs/DB.dbml) | DB 스키마 — dbdiagram.io 에 붙여넣으면 ERD |
| [docs/api/API.yml](docs/api/API.yml) | OpenAPI 3.0 명세 |
| [docs/api/README.md](docs/api/README.md) | API 공통 규약 — 응답 봉투, 인증, 오류 코드, Enum |
| [docs/architecture.md](docs/architecture.md) | 시스템 아키텍처 — FE·BE·DB 구조, 폴더 구조, 테이블 역할 |
| [docs/diagrams/](docs/diagrams/) | 시퀀스 다이어그램 5장 ([PDF](docs/ZipSa-시퀀스다이어그램.pdf)) |
| [docs/ai-prompts.md](docs/ai-prompts.md) | AI 프롬프트 원문과 입출력 JSON 스키마 |

문서 PDF 는 `scripts/build_*.py` 로 다시 만듭니다.

---

## 팀이 반드시 지킬 것

1. **`.env` 와 API 키를 커밋하지 않습니다.** 한 번 올라가면 히스토리에 영원히 남습니다.
2. **스키마는 백엔드와 크롤러가 공유합니다.** 컬럼 변경은 양쪽 합의 후 새 마이그레이션으로 추가하고 `docs/DB.dbml` 도 함께 고칩니다. **이미 적용된 마이그레이션 파일은 수정하지 않습니다.**
3. **크롤러는 판단하지 않습니다.** 수집·정규화·적재까지만 하고, 추천·매칭·통계는 전부 Spring 이 합니다.
4. **`main` 에 직접 push 하지 않습니다.** 자세한 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md).
