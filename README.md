# ZIP 보금자리 (ZipSa)

청년 주거 정책 · 공공임대 · 실거래가를 한 곳에서 확인하는 서비스입니다.

> **범위 원칙: 와이어프레임에 그려진 11개 화면이 유일한 기준입니다.**
> 화면에 없는 기능은 만들지 않습니다. 기능을 추가하려면 와이어프레임을 먼저 그리세요.

---

## 구성

```
Vue.js (SPA)  →  Spring Boot (+ Spring AI)  →  PostgreSQL
                                                    ↑
                                        Python 크롤러 (직접 write)
```

| 디렉터리 | 내용 | 스택 |
| --- | --- | --- |
| `frontend/` | 웹 클라이언트 | Vue 3 · Vite · Pinia |
| `backend/` | API 서버 (유일한 외부 진입점) | Spring Boot 3.5 · Java 21 · Spring AI |
| `crawler/` | 정책·공고·실거래가 수집 배치 | Python 3.11 · psycopg |
| `docs/` | API 명세 · DB 스키마 | — |

---

## 실행 방법 두 가지

| | 언제 쓰나 | 명령 |
| --- | --- | --- |
| **A. Docker 한 방** | 처음 받았을 때 · 데모 · "일단 돌려보고 싶다" | `docker compose up --build` |
| **B. 로컬 실행** | **개발할 때** — 코드 고치면 바로 반영됨 | 터미널 2개 (아래 참고) |

> 개발 중에는 **B** 를 쓰세요. A 는 코드를 고칠 때마다 이미지를 다시 빌드해야 해서 느립니다.

---

## 공통 준비 (최초 1회)

Java 21 · Node 20+ · Python 3.11+ · Docker (A 방식) 또는 PostgreSQL 17 (B 방식)

```bash
cp .env.example .env
openssl rand -base64 48      # 출력값을 .env 의 JWT_SECRET 에 붙여넣기
```

`.env` 의 `POSTGRES_PASSWORD` 도 아무 값으로 채웁니다. **`.env` 는 절대 커밋하지 않습니다.**

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
- **DB 까지 완전히 초기화**: `docker compose down -v` (볼륨 삭제 — 데이터 전부 사라짐)

### 크롤러 실행 (배치)

크롤러는 상주 서비스가 아니라 배치라서 `up` 으로는 뜨지 않습니다.

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
set -a && . ../.env && set +a     # Spring Boot 는 .env 를 자동으로 읽지 않습니다. 빼먹으면 기동 실패
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

# 실거래가 — 서울 25개 구 × 최근 3개월 (10분 남짓 걸립니다)
python -m zipsa_crawler.main --target transaction

# 한 지역만 빠르게 확인
python -m zipsa_crawler.main --target transaction --region 11200 --months 1
```

먼저 키가 제대로 들어갔는지 확인하세요. 인코딩/디코딩 실수까지 잡아줍니다.

```bash
crawler/.venv/bin/python scripts/check_keys.py
```

---

## 동작 확인

회원가입 → 로그인 → 마이페이지에 본인 닉네임과 `20대 후반 · 미혼 · 직장인` 태그가 뜨면
**프론트 ↔ 백엔드 ↔ DB 가 전부 연결된 것입니다.**

---

## 개발용 더미 데이터

정책·회원·조회이력이 없으면 목록도 추천도 확인할 수 없어서, 시드 생성기를 넣어뒀습니다.

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

- 더미 계정 로그인: `seed_user_001` ~ `seed_user_160` / 비밀번호 `Demo!pass12`
- **시드 고정**이라 누가 돌려도 같은 데이터가 나옵니다. 삭제는 `seed_user_%` / `SEED-POLICY-%` 행만 지우므로 직접 가입한 계정은 남습니다.
- 조회로그는 **군집별로 선호 패턴을 심어놨습니다.** 균등 난수면 추천이 그냥 인기순이 돼버려서 로직 검증이 안 됩니다.

심어둔 패턴이 복원되는지 확인:

```bash
psql -h localhost -U zipsa -d zipsa -f scripts/verify_seed.sql
```

> Docker 스택(5433)에 넣으려면 `POSTGRES_PORT=5433 crawler/.venv/bin/python scripts/seed_dev_data.py`


---

## 지금까지 구현된 것

| 영역 | 상태 |
| --- | --- |
| 인증 · 회원 (오퍼레이션 1~8) | ✅ 동작 — 회원가입 6단계 온보딩, 로그인, 토큰 재발급/회전, 로그아웃, 프로필 조회·수정, 탈퇴 |
| **실거래가 (화면 11)** | ✅ 동작 — 크롤러(국토부 API + 카카오 지오코딩) → DB → 조회 API → 지도 화면까지 관통 |
| DB 스키마 | ✅ Flyway `V1` ~ `V4` |
| 정책 · 공공임대 · 커뮤니티 · 대출 | ⬜ 미구현 — `docs/api/` 명세대로 담당자가 채웁니다 |
| 정책 · 공공임대 크롤러 | ⬜ 미구현 — 실거래가 수집기(`transaction/`)를 참고하세요 |

### 실거래가 화면을 보려면 (화면 11)

지도는 **카카오 개발자 콘솔에 도메인을 등록해야** 뜹니다. 등록 전에는 화면에 안내가 표시됩니다.

> developers.kakao.com > 내 애플리케이션 > 앱 설정 > **플랫폼 > Web > 사이트 도메인**
> `http://localhost:5173` (로컬) 과 `http://localhost:3000` (Docker) 을 **둘 다** 등록

---

## 문서

| 문서 | 내용 |
| --- | --- |
| [CONTRIBUTING.md](CONTRIBUTING.md) | 브랜치·커밋·PR·코드리뷰 규칙 (**작업 전 필독**) |
| [docs/api/README.md](docs/api/README.md) | API 공통 규약 — 응답 봉투, 인증, 오류 코드, Enum |
| [docs/api/](docs/api/) | 도메인별 오퍼레이션 명세 |
| [docs/DB.dbml](docs/DB.dbml) | DB 스키마 (dbdiagram.io 에 붙여넣으면 ERD) |
| [docs/architecture.md](docs/architecture.md) | 시스템 아키텍처 — FE·BE·DB 구조, 폴더 구조, DB 연동 확인 ([PDF](docs/ZipSa-시스템아키텍처.pdf), `python3 scripts/build_architecture_pdf.py` 로 갱신) |
| [docs/ai-prompts.md](docs/ai-prompts.md) | AI 프롬프트 명세 — 정책·뉴스·대출레포트 시스템/유저 프롬프트 원문, 입출력 JSON 스키마 실측 |

---

## 팀이 반드시 지킬 것

1. **`.env` 와 API 키를 커밋하지 않습니다.** 한 번 올라가면 히스토리에 영원히 남습니다.
2. **스키마는 백엔드와 크롤러가 공유합니다.** 컬럼 변경은 양쪽 합의 후 새 마이그레이션(`V2__*.sql`)으로 추가하고 `docs/DB.dbml` 도 함께 고칩니다. **이미 적용된 마이그레이션 파일은 수정하지 않습니다.**
3. **크롤러는 판단하지 않습니다.** 수집·정규화·적재까지만 하고, 추천·매칭·통계는 전부 Spring 이 합니다.
4. **`main` 에 직접 push 하지 않습니다.** 자세한 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md).
