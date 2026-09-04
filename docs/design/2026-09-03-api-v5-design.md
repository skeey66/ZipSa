# ZipSa API 설계 v5 — 명세 v4 × 와이어프레임 정합

> 기준 문서 2개
> - **REST API 명세서 v4** (Version 0.3 · 2026.09 · 작성자 이효빈)
> - **Figma 와이어프레임** 11개 화면
>
> 이 문서는 둘을 대조해 **범위·규약·스키마를 확정**하고, 그 과정에서 발견한
> 명세 결함과 해소 방법을 기록합니다. 구현은 이 문서를 따릅니다.

---

## 0. 확정된 결정

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| 응답 봉투 | **v4 형식 `{success, data, error}`** 로 교체 | 명세가 팀 공용 문서. 코드를 문서에 맞춥니다. |
| 관리자(ADMIN) | **포함.** 와이어프레임을 새로 그림 | 유저 정지·게시글 강제삭제·크롤 운영이 필요 |
| 계약서 AI 분석(10장) | **제외** | OCR 미사용 결정 유지. 업로드 화면도 없음 |
| 뉴스(5장) | **포함.** 「정보」 하위 메뉴 | 명세 개요가 "정책·뉴스·공공임대·실거래가" 로 규정 |

---

## 1. 아키텍처 — 서버 3개가 아니라 2개 + 배치

명세는 Base URL 을 셋으로 나눕니다.

```
Base URL (Spring)   /api
Base URL (Data)     https://data.api.example.com
Base URL (SpringAI) https://ai.api.example.com
```

**실제로는 나눌 이유가 없습니다.** "Data" 와 "SpringAI" 가 하는 일은
① 실거래가 조회 ② 정책 AI 요약 두 가지인데, ①은 DB 읽기일 뿐이고
②는 LLM 호출 한 번입니다. Spring AI 로 같은 프로세스 안에서 처리됩니다.

서버를 나누면 인증 토큰 검증·CORS·배포·로컬 실행이 전부 3배가 됩니다.
**학기 프로젝트에서 이 비용은 회수되지 않습니다.**

```
Vue (SPA)  →  Spring Boot (+ Spring AI)  →  PostgreSQL
                                               ↑
                                   Python 크롤러 (배치, 직접 write)
```

명세의 `/api/v1/*` 경로는 **같은 Spring 앱 안의 경로**로 흡수합니다.
따라서 아래 중복 엔드포인트는 하나로 합칩니다.

| 명세 | 통합 후 |
| --- | --- |
| `/api/transactions` (기존) + `/api/v1/transactions` (SpringAI) | `/api/transactions` |
| `/api/transactions/{id}` + `/api/v1/transactions/{id}` | `/api/transactions/{id}` |
| `/api/v1/regions` | `/api/regions` |
| `/api/v1/transactions/stats` | `/api/transactions/stats` |
| `/api/v1/admin/crawl/*` | `/api/admin/crawl/*` |

---

## 2. 명세 v4 에서 발견한 결함

구현 전에 고쳐야 하는 것들입니다. **번호는 명세의 절 번호입니다.**

### 2.1 같은 정보를 두 벌로 받습니다 (3.1 회원가입, 3.6 내 정보)

```json
{
  "age": 27,              "ageRange": "AGE_20S_LATE",
  "job": "직장인",         // Job enum 은 STUDENT/EMPLOYEE/...
  "annualIncome": 4000,   "salaryRange": "RANGE_3000_4000",
  "username": "hong123",  "userId": "hong123",
  "name": "홍길동",        "nickname": "길동"
}
```

`age` 와 `ageRange` 가 어긋나면 어느 쪽이 참인지 판단할 수 없습니다.
`job: "직장인"` 은 한글 문자열이라 enum 과 매칭도 안 됩니다.

**해소** — 와이어프레임 온보딩이 수집하는 **구간값(enum)만** 남깁니다.
`age`, `annualIncome`, `username`, `name` 을 제거하고
`loginId`, `nickname`, `ageRange`, `job`, `salaryRange`, `maritalStatus` 로 확정합니다.

### 2.2 공공임대와 공공주택이 중복입니다 (2장 목록, 6장)

`/api/housings`(공공임대 목록/상세)와 `/api/public-housings`(공공주택 목록/상세)가
같은 대상을 가리킵니다. 와이어프레임 화면 10은 하나뿐입니다.

**해소** — `/api/public-housings` 하나로 통합. `/api/housings` 폐기.

### 2.3 대출 통계가 두 개입니다 (9.3, 9.7)

| | URL | 인증 | 응답 |
| --- | --- | --- | --- |
| LOAN-003 | `/api/loans/statistics` | O | `sampleCount, averageExpectedAmount, averageActualAmount, difference` |
| LOAN-007 | `/api/loans/stats` | X | `sampleSize, avgActualLimit, avgActualRate, vsEstimatedDiffRate` |

이름도 인증도 필드도 다릅니다. 커뮤니티 배너(화면 08)는 **예상 vs 실제** 막대를
그리므로 LOAN-003 쪽이 맞습니다.

**해소** — `/api/loans/statistics` 로 통합, **인증 필요**. `/api/loans/stats` 폐기.

### 2.4 JSON 키에 한글과 슬래시가 있습니다 (6.2 공공주택 상세)

```json
{ "임대조건": "...", "면적": "59.8㎡", "보증금/임대료": "...", "신청자격": "..." }
```

**해소** — `eligibility`, `exclusiveArea`, `deposit`/`monthlyRent`, `applyQualification`
으로 영문화하고, 면적은 단위를 뗀 숫자(`59.8`)로 보냅니다.

### 2.5 남의 대출 정보가 비로그인에 공개됩니다 (9.6)

`GET /api/loans/actual/{loanId}` 가 **인증 X** 인데 응답에
`authorProfile: {ageRange, job}` 이 들어갑니다. 대출 한도·금리·은행명은
민감 정보이고, 프로필이 붙으면 특정 가능성이 올라갑니다.

**해소** — **인증 필요**로 바꾸고, 개별 조회는 **본인 것만** 허용합니다.
남의 값은 `/api/loans/statistics` 의 **집계로만** 제공합니다.
명세 13장 4)의 "집계 결과만 반환한다" 원칙과도 이쪽이 맞습니다.

### 2.6 게시글에 실명이 노출됩니다 (8.2)

```json
"author": { "userId": 10, "name": "홍길동" },
"authorNickname": "길동"
```

**해소** — `author.name`(실명) 제거. `authorNickname` 만 남깁니다.
애초에 회원가입에서 실명을 받지 않기로 했습니다(2.1).

### 2.7 페이지네이션 응답이 4가지입니다

| 위치 | 형태 |
| --- | --- |
| 정책·게시글 | `{content, page, size, totalElements, totalPages}` |
| 실거래(SpringAI) | `{items, total}` |
| 댓글 | `{comments: []}` |
| 내 대출 | `{loans: []}` |

**해소** — **목록은 전부 `{content, page, size, totalElements, totalPages}`** 로 통일합니다.
페이지네이션이 없는 목록도 같은 형태로 감쌉니다. 프론트에서 분기가 사라집니다.

### 2.8 AI 실패가 500 입니다 (4.5)

외부 LLM 장애는 우리 서버의 버그가 아닙니다. 500 은 알림·모니터링을 오염시킵니다.

**해소** — `503 AI_SUMMARY_FAILED`. 그리고 **요약 실패가 정책 상세 화면을 깨뜨리지 않습니다.**
요약 영역만 비고 원문은 그대로 보입니다.

### 2.9 API ID 에 결번이 있습니다

`POLICY-003`, `AI-001`, `ADMIN-003`, `ADMIN-005`~`009` 가 없고
`4.3`, `4.6`, `8.6` 절이 비어 있습니다. 삭제된 항목인지 누락인지 알 수 없습니다.

**해소** — 번호를 다시 매기지 않고 **결번을 명시**합니다(이력 추적이 더 중요).

---

## 3. 공통 규약

### 3.1 응답 봉투

```json
{ "success": true,  "data": { ... },  "error": null }
{ "success": false, "data": null,     "error": { "code": "POLICY_NOT_FOUND", "message": "정책을 찾을 수 없습니다." } }
```

- 성공이면 `error` 가 `null`, 실패면 `data` 가 `null` 입니다. 둘 다 채우지 않습니다.
- HTTP 상태 코드가 1차 신호이고, `error.code` 는 **화면 분기용 2차 신호**입니다.
- 204(No Content)는 본문을 보내지 않습니다.

> ⚠️ **현재 구현은 `{isSuccess, message, errorCode, payload}` 입니다.**
> 인증·실거래가·커뮤니티가 이미 이 형식으로 동작 중이라 교체 작업이 필요합니다.
> 범위는 4.6절 참고.

### 3.2 목록 응답

```json
{ "content": [ ... ], "page": 0, "size": 20, "totalElements": 137, "totalPages": 7 }
```

### 3.3 인증

- `Authorization: Bearer {accessToken}`
- Access 30분 / Refresh 14일, **재발급 시 Refresh 도 함께 교체**(회전)
- 권한 표기: `X` 비로그인 허용 · `O` 로그인 필요 · `A` ADMIN 전용

### 3.4 Enum (확정)

| Enum | 값 | 명세 v4 대비 |
| --- | --- | --- |
| `Role` | `USER`, `ADMIN` | 그대로 |
| `UserStatus` | `ACTIVE`, `SUSPENDED`, `DELETED` | 그대로 |
| `Job` | `STUDENT`, `EMPLOYEE`, `SELF_EMPLOYED`, `JOB_SEEKER`, `ETC` | 그대로 |
| `AgeRange` | `AGE_10S`, `AGE_20S_EARLY`, `AGE_20S_LATE`, `AGE_30S_EARLY`, `AGE_30S_LATE`, `AGE_40S_OVER` | 그대로 (현재 구현의 `ETC` → `AGE_40S_OVER` 로 교체) |
| `SalaryRange` | `UNDER_2000`, `RANGE_2000_3000`, `RANGE_3000_4000`, `RANGE_4000_5000`, `RANGE_5000_7000`, `OVER_7000` | 그대로 |
| **`MaritalStatus`** | `SINGLE`, `MARRIED` | **`Gender` 대체** — 와이어프레임 온보딩이 결혼여부를 묻습니다 |
| `VerifyStatus` | `PENDING`, `VERIFIED`, `REJECTED`, `FLAGGED_ANOMALY` | 그대로 |
| `PolicyCategory` | `HOUSING`, `LOAN`, `PUBLIC_HOUSING`, `SUPPLY` | 명세에 없어 신규 |
| `PostCategory` | `FREE`, `INFO`, `QUESTION`, `LOAN` | 명세는 INFO/QUESTION 만 언급 |
| `HousingType` | `HAPPY_HOUSE`, `PURCHASE_RENTAL`, `JEONSE_RENTAL`, `NATIONAL_RENTAL` | 신규 |
| `DealType` | `SALE`, `JEONSE`, `MONTHLY` | 신규 |
| `CrawlJobStatus` | `PENDING`, `RUNNING`, `SUCCESS`, `FAILED` | 신규 |

> **`Gender` 를 뺀 이유**: 와이어프레임 회원가입 6단계는 나이대·결혼여부·직업·연소득을
> 묻습니다. 성별을 묻는 단계가 없고, 정책 매칭에 실제로 쓰이는 건 신혼부부 정책
> 대상 판별(결혼여부)입니다.

---

## 4. API 목록 (확정)

`X` 비로그인 · `O` 로그인 · `A` ADMIN

### 4.1 회원 — 화면 02·03·05

| API ID | Method | URI | 기능 | 인증 |
| --- | --- | --- | --- | --- |
| AUTH-001 | POST | `/api/auth/signup` | 회원가입 | X |
| AUTH-004 | GET | `/api/auth/check-id` | 아이디 중복확인 | X |
| AUTH-002 | POST | `/api/auth/login` | 로그인 | X |
| AUTH-005 | POST | `/api/auth/reissue` | 토큰 재발급 | X |
| AUTH-003 | POST | `/api/auth/logout` | 로그아웃 | O |
| USER-001 | GET | `/api/users/me` | 내 정보 조회 | O |
| USER-002 | PATCH | `/api/users/me` | 내 정보 수정 | O |
| USER-003 | DELETE | `/api/users/me` | 회원 탈퇴 | O |

**✅ 구현 완료** — 봉투 교체와 `AgeRange.ETC → AGE_40S_OVER` 만 남았습니다.

### 4.2 정책 — 화면 01·06·07

| API ID | Method | URI | 기능 | 인증 |
| --- | --- | --- | --- | --- |
| POLICY-001 | GET | `/api/policies` | 목록/검색/필터 | X |
| POLICY-002 | GET | `/api/policies/{policyId}` | 상세 | X |
| POLICY-004 | GET | `/api/policies/recommend` | 맞춤 정책 | O |
| POLICY-005 | GET | `/api/policies/{policyId}/summary` | AI 요약 | O |
| POLICY-006 | POST | `/api/policies/{policyId}/bookmark` | 북마크 등록 | O |
| POLICY-007 | DELETE | `/api/policies/{policyId}/bookmark` | 북마크 해제 | O |
| POLICY-008 | GET | `/api/policies/bookmarks` | 내 북마크 목록 | O |

> **맞춤 정책(POLICY-004)의 정의를 확정합니다.**
> 명세는 `relevanceRate` 와 `matchReason` 을 요구합니다. 이건 **규칙 기반 매칭**입니다.
> 프로필(나이대·직업·연소득·결혼여부) ↔ 정책의 지원 대상 조건을 대조해 점수를 냅니다.
> LLM 을 쓰지 않으므로 결과가 흔들리지 않고 비용도 들지 않습니다.
> `policy_views` 기반 행동 추천은 **데이터가 쌓인 뒤 2차로** 얹습니다.

### 4.3 뉴스 — 화면 「정보」 하위 (⚠️ 와이어프레임 필요)

| API ID | Method | URI | 기능 | 인증 |
| --- | --- | --- | --- | --- |
| NEWS-001 | GET | `/api/news` | 뉴스 목록 | X |
| NEWS-002 | GET | `/api/news/{newsId}` | 뉴스 상세 | X |

> ⚠️ **저작권 주의.** 언론사 기사 본문은 언론사 저작물입니다.
> `title`, `summary`(2~3줄), `sourceUrl`, `pressName`, `publishedAt` 만 저장하고
> **본문은 저장하지 않습니다.** 상세 화면은 요약 + 원문 링크로 구성합니다.
> 네이버 뉴스는 `robots.txt` 가 전면 차단이므로 크롤링 대상이 아닙니다.
> 수집은 **언론사 RSS** 또는 **네이버 검색 오픈 API** 를 씁니다.
>
> NEWS-002 의 명세 응답에 `content`(기사 내용)가 있으나 위 사유로 제거합니다.

### 4.4 공공주택 — 화면 09·10

| API ID | Method | URI | 기능 | 인증 |
| --- | --- | --- | --- | --- |
| HOUSING-001 | GET | `/api/public-housings` | 목록/검색 | X |
| HOUSING-002 | GET | `/api/public-housings/{id}` | 상세 | X |
| HOUSING-003 | POST | `/api/public-housings/{id}/bookmark` | 북마크 등록 | O |
| HOUSING-004 | DELETE | `/api/public-housings/{id}/bookmark` | 북마크 해제 | O |

### 4.5 실거래가 — 화면 11

| API ID | Method | URI | 기능 | 인증 |
| --- | --- | --- | --- | --- |
| TRANSACTION-001 | GET | `/api/transactions` | 목록/검색 | X |
| TRANSACTION-002 | GET | `/api/transactions/{transactionId}` | 상세 | X |
| TRANSACTION-005 | GET | `/api/regions` | 지역 코드 | X |
| TRANSACTION-006 | GET | `/api/transactions/stats` | 시세 통계 | X |
| — | GET | `/api/transactions/map` | 지도 마커(단지 집계) | X |

**✅ 구현 완료** — 서울 25구 39,262건 적재, 전국 218개 지역 코드 확보.
`/api/transactions/map` 은 명세에 없지만 **화면 11에 필수**입니다.
거래 수만 건을 그대로 내려보내면 마커가 겹쳐 지도를 읽을 수 없습니다.

### 4.6 커뮤니티 — 화면 01·08

| API ID | Method | URI | 기능 | 인증 |
| --- | --- | --- | --- | --- |
| POST-001 | GET | `/api/posts` | 게시글 목록 | X |
| POST-002 | GET | `/api/posts/{postId}` | 게시글 상세 | X |
| POST-003 | POST | `/api/posts` | 게시글 작성 | O |
| POST-004 | PATCH | `/api/posts/{postId}` | 게시글 수정 | O |
| POST-005 | DELETE | `/api/posts/{postId}` | 게시글 삭제 | O |
| POST-007 | POST | `/api/posts/{postId}/bookmark` | 즐겨찾기 등록 | O |
| POST-008 | DELETE | `/api/posts/{postId}/bookmark` | 즐겨찾기 취소 | O |
| — | POST | `/api/posts/{postId}/likes` | 좋아요 토글 | O |
| COMMENT-001 | GET | `/api/posts/{postId}/comments` | 댓글 조회 | X |
| COMMENT-002 | POST | `/api/posts/{postId}/comments` | 댓글 작성 | O |
| COMMENT-003 | PATCH/DELETE | `/api/comments/{commentId}` | 댓글 수정/삭제 | O |

**✅ 대부분 구현 완료.** 남은 것: 즐겨찾기(POST-007/008), 댓글 수정, 봉투 교체.

> 명세에 **좋아요 API 가 없습니다.** 그런데 8.1·8.2 응답에는 `likeCount`,
> `isLikedByMe` 가 있습니다. 누를 방법이 없는 카운터입니다. 토글 API 를 추가했습니다.

> 게시글의 `relatedPolicyId` / `relatedPublicHousingId` / `relatedLoanId` 는
> 명세 14장의 "커뮤니티 ↔ 대출비교 연계" 를 위한 것입니다. **컬럼을 추가합니다.**

### 4.7 대출 비교 — 화면 05·08

| API ID | Method | URI | 기능 | 인증 |
| --- | --- | --- | --- | --- |
| LOAN-004 | POST | `/api/loans/estimate` | 예상 한도 계산 | O |
| LOAN-005 | POST | `/api/loans/actual` | 실제 대출 등록 | O |
| LOAN-002 | GET | `/api/loans/me` | 내 대출 정보 | O |
| LOAN-006 | GET | `/api/loans/actual/{loanId}` | 실제 대출 상세 | **O (본인만)** |
| LOAN-003 | GET | `/api/loans/statistics` | 유사 사용자 통계 | O |
| LOAN-008 | POST | `/api/loans/actual/{loanId}/verify` | 문서 검증 | A |

> **표본 3건 미만 구간은 통계를 내지 않습니다.** 명세 9.7 비고 그대로입니다.
> 표본이 적으면 개인 값이 그대로 드러납니다.

> LOAN-001 `POST /api/loans`(예상+실제 동시 등록)는 **폐기**합니다.
> 예상은 계산 결과이고 실제는 사용자 입력이라 생성 시점이 다릅니다.
> LOAN-004/005 로 분리하는 편이 맞습니다.

### 4.8 관리자 — 화면 12 (⚠️ 신규, 와이어프레임 필요)

| API ID | Method | URI | 기능 | 인증 |
| --- | --- | --- | --- | --- |
| ADMIN-001 | GET | `/api/admin/users` | 유저 목록/검색 | A |
| ADMIN-002 | PATCH | `/api/admin/users/{userId}/status` | 유저 상태 변경 | A |
| ADMIN-004 | DELETE | `/api/admin/posts/{postId}` | 게시글 강제 삭제 | A |
| TRANSACTION-007 | POST | `/api/admin/crawl/trigger` | 크롤링 수동 실행 | A |
| ADMIN-010 | GET | `/api/admin/crawl/jobs/{jobId}` | 크롤 Job 상태 조회 | A |

> **첫 ADMIN 계정은 API 로 만들지 않습니다.** 권한 상승 경로가 되기 때문입니다.
> DB 에서 직접 `UPDATE users SET role='ADMIN' WHERE login_id='...'` 로 지정합니다.

---

## 5. DB 스키마 변경

현재 `V1`~`V6` 이 적용되어 있습니다. 이 설계에 맞추려면:

| 마이그레이션 | 내용 |
| --- | --- |
| `V7__user_role_and_status.sql` | `users.role` 추가(`USER` 기본), `UserStatus` 에 `SUSPENDED` 허용, `AgeRange.ETC` → `AGE_40S_OVER` 데이터 이관 |
| `V8__news.sql` | `news` 테이블 (제목·요약·언론사·원문URL·발행일. **본문 없음**) |
| `V9__post_relations.sql` | `posts.related_policy_id`, `related_public_housing_id`, `related_loan_id` |
| `V10__post_bookmarks.sql` | `post_bookmarks` (게시글 즐겨찾기, 좋아요와 별개) |
| `V11__loan_verify.sql` | `loan_actuals.verify_status`, `document_url`, `verified_by`, `verified_at`, `memo` |

**변경하지 않는 것** — `contracts`, `contract_risk_items` (계약서 AI 제외).

---

## 6. 현재 구현 → 이 설계로 옮기는 순서

이미 동작 중인 코드가 있으므로 **한 번에 갈아엎지 않습니다.**

| 단계 | 작업 | 영향 |
| --- | --- | --- |
| 1 | **응답 봉투 교체** — `ApiResponse` 레코드, `GlobalExceptionHandler`, JWT 필터, 프론트 `client.js` 인터셉터 | 전 도메인. 가장 먼저 해야 뒤에 만드는 것들이 새 형식으로 태어납니다 |
| 2 | Enum 정리 (`AgeRange`, `MaritalStatus` 확정) + `users.role` | 회원가입·마이페이지 |
| 3 | 정책 도메인 (크롤러 + API 7개 + 화면 3개) | 온통청년 키 필요 |
| 4 | 공공주택 도메인 (LH 크롤러 + API 4개 + 화면 2개) | data.go.kr LH 활용신청 필요 |
| 5 | 커뮤니티 보완 (즐겨찾기·댓글수정·연계 컬럼) | — |
| 6 | 대출 비교 (API 6개 + 마이페이지 탭 + 커뮤니티 배너) | — |
| 7 | 뉴스 (RSS 수집 + API 2개 + 화면) | 와이어프레임 필요 |
| 8 | 관리자 (API 5개 + 화면) | 와이어프레임 필요 |

**1번을 먼저 하는 이유**: 나중에 하면 이미 만든 화면을 전부 다시 손봐야 합니다.
지금은 인증·실거래가·커뮤니티 3개 도메인만 고치면 됩니다.

---

## 7. 아직 와이어프레임이 없는 화면

구현 전에 팀이 그려야 합니다.

| 화면 | 필요한 이유 |
| --- | --- |
| **뉴스 목록·상세** | 「정보」 하위 메뉴로 확정했으나 화면이 없습니다 |
| **관리자** | 유저 관리·게시글 관리·크롤 운영 3개 탭 |
| **게시글 작성** | 「글쓰기」 버튼은 있는데 이동할 화면이 없습니다 (임시 구현해 둠) |
| **실제 대출 등록** | 커뮤니티 배너의 「실제」 막대 원천인데 입력 화면이 없습니다 |
| **내 대출 한도 계산** | 「내 대출 한도 계산하기」 버튼의 목적지 |

---

## 8. 명세 v4 에서 제외한 항목과 사유

| 항목 | 사유 |
| --- | --- |
| 10장 계약서 AI 분석 전체 | OCR 미사용 결정. 업로드 화면 없음 |
| `/api/housings` (공공임대) | `/api/public-housings` 와 중복 |
| `/api/v1/transactions` 계열 | `/api/transactions` 와 중복 (서버 분리 안 함) |
| `/api/loans/stats` (LOAN-007) | `/api/loans/statistics` 와 중복 |
| `POST /api/loans` (LOAN-001) | LOAN-004/005 로 분리 |
| `Gender` enum | 와이어프레임이 결혼여부를 수집 → `MaritalStatus` |
| 회원 `age`, `annualIncome`, `username`, `name` | 구간 enum 과 중복 |
| 뉴스 `content` | 언론사 저작권 |
