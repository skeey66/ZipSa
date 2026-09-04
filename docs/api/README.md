# ZipSa API 명세서 — 공통 규약

> **범위 원칙: 와이어프레임에 그려진 11개 화면이 유일한 기준입니다.**
> 화면에 없는 기능은 명세하지 않고 구현하지 않습니다. 기능을 추가하려면 **와이어프레임을 먼저 그리고** PR로 이 문서를 함께 수정합니다.

## 📘 기계가 읽는 명세: [API.yml](API.yml)

전체 엔드포인트(41개)의 **Method · Path · Request · Response · 예외(400/404/500…)** 는
`docs/api/API.yml` 에 OpenAPI 3.0.0 으로 정리돼 있으며, **컨트롤러 구현이 기준**입니다.

| 보는 방법 | 하는 법 |
| --- | --- |
| Swagger Editor | <https://editor.swagger.io/> 에서 `File > Import file` 로 `API.yml` 열기 |
| Postman | `Import > File` 로 `API.yml` 선택 → 컬렉션이 자동 생성됩니다 |
| 로컬 서버 | 백엔드 기동 후 <http://localhost:8080/swagger-ui.html> (springdoc 이 코드에서 생성) |
| 검증 | `npx @redocly/cli lint docs/api/API.yml` |
| **인쇄·배포용 PDF** | [ZipSa-API-명세서.pdf](ZipSa-API-명세서.pdf) — 38쪽. `python3 scripts/build_api_spec_pdf.py` 로 다시 생성 |

> **엔드포인트별 상세는 이 문서가 아니라 [API.yml](API.yml) 에 있습니다.**
> 도메인별로 나눠 쓰던 `01-auth-user.md` 같은 문서는 API.yml 로 합치면서 없앴습니다.
> 두 벌을 유지하면 반드시 한쪽이 낡습니다.

---

## 1. 서비스 구성

```
                  ┌──────────────┐
   사용자 ───────▶ │  Vue.js (SPA) │
                  └───────┬──────┘
                          │ ① 모든 요청은 Spring 하나로만
                          ▼
              ┌───────────────────────────┐
              │  Spring Boot (+ Spring AI) │  인증 · 비즈니스 로직 · LLM 호출
              └────────────┬──────────────┘
                           │ ② 읽기/쓰기
                           ▼
                      ┌─────────┐
                      │   DB    │
                      └────▲────┘
                           │ ③ 직접 write
                  ┌────────┴────────┐
                  │  Python 크롤러   │  정책 · 모집공고 · 실거래가 수집
                  └─────────────────┘
```

**서버는 3개입니다.** AI 서버(FastAPI)를 따로 두지 않고, LLM 호출은 Spring AI 로 Spring Boot 안에서 처리합니다. 이 프로젝트가 AI 에 요구하는 건 **정책 AI 요약 하나뿐**이라 서버를 나눌 이유가 없습니다.

### 각 서비스의 책임

| 서비스 | 책임 | 하지 않는 것 |
| --- | --- | --- |
| **Vue.js** | 화면 렌더링, 상태 관리, 토큰 보관 | 비즈니스 로직 없음 |
| **Spring Boot** | **유일한 외부 진입점.** 인증/인가, 비즈니스 로직, DB 읽기·쓰기, **Spring AI 로 LLM 호출** | 크롤링 안 함 |
| **Python 크롤러** | 정책·모집공고·실거래가 수집 후 **DB 직접 write** | HTTP API 제공 안 함 (배치로만 동작), 판단 로직 없음 |

### ⚠️ 이 구조에서 반드시 지킬 것

1. **크롤러가 DB 에 직접 쓰므로, 스키마는 크롤러 담당자와 백엔드 담당자가 공유합니다.** 컬럼 변경은 반드시 양쪽 합의 후 PR 로 진행합니다. → [DB.dbml](../DB.dbml)
2. **크롤러는 업무 로직을 갖지 않습니다.** 수집·정규화·적재까지만 하고, 판단(추천, 매칭, 통계)은 전부 Spring 이 합니다.
3. **LLM API 키는 `.env` 로만 주입합니다.** 절대 커밋하지 않습니다.

---

## 2. Base URL

| 환경 | URL |
| --- | --- |
| 로컬 | `http://localhost:8080` |
| 개발 | `https://dev-api.zipsa.example.com` |
| 운영 | `https://api.zipsa.example.com` |

---

## 3. 공통 응답 포맷

모든 응답은 성공·실패 관계없이 같은 봉투(envelope)로 감쌉니다.
프론트가 한 갈래로 처리할 수 있게 하기 위함입니다. 기준은 `common/ApiResponse.java` 입니다.

### 성공

```json
{
  "success": true,
  "data": { },
  "error": null
}
```

### 실패

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "USER_ID_DUPLICATED",
    "message": "이미 사용 중인 아이디입니다."
  }
}
```

| 항목명(영문) | 항목명(국문) | 항목크기 | 항목구분 | 샘플데이터 | 항목설명 |
| --- | --- | --- | --- | --- | --- |
| success | 성공여부 | Boolean | 1 | true | 요청 성공 여부 |
| data | 응답본문 | Object | 0 | { } | 실제 데이터. 실패 시 null |
| error | 오류정보 | Object | 0 | { } | 성공 시 null |
| error.code | 오류코드 | String | 0 | USER_ID_DUPLICATED | 7장 오류 코드 참고 |
| error.message | 오류메시지 | String | 0 | 이미 사용 중인 아이디입니다. | 사용자에게 보여줄 문구 |

> **항목구분**: 필수(1) · 옵션(0)

---

## 4. 인증

**JWT (Access / Refresh)** 를 사용합니다.

| 항목 | 값 |
| --- | --- |
| 전달 방식 | `Authorization: Bearer {accessToken}` 헤더 |
| Access Token 만료 | 30분 |
| Refresh Token 만료 | 14일 |
| 갱신 | 401 응답 시 오퍼레이션 4(토큰 재발급) 호출 |

### 인증 표기

| 표기 | 의미 |
| --- | --- |
| `없음` | 비로그인 허용 |
| `USER` | 로그인 필요 |

> **관리자 권한(ADMIN)은 없습니다.** 관리자 화면이 와이어프레임에 없기 때문입니다. 운영상 필요한 조작은 DB 직접 접근 또는 크롤러 재실행으로 처리합니다.

---

## 5. 공통 페이지네이션

| 항목명(영문) | 항목명(국문) | 항목크기 | 항목구분 | 샘플데이터 | 항목설명 |
| --- | --- | --- | --- | --- | --- |
| page | 페이지번호 | Number | 0 | 0 | 0부터 시작. 기본값 0 |
| size | 페이지크기 | Number | 0 | 20 | 기본값 20, 최대 100 |
| sort | 정렬기준 | String | 0 | createdAt,desc | `필드명,asc\|desc` |

페이징 응답의 `payload` 공통 구조:

| 항목명(영문) | 항목명(국문) | 항목크기 | 항목구분 | 샘플데이터 | 항목설명 |
| --- | --- | --- | --- | --- | --- |
| content | 목록 | Array | 1 | [ ... ] | 조회된 항목 배열 |
| page | 현재페이지 | Number | 1 | 0 | 현재 페이지 번호 |
| size | 페이지크기 | Number | 1 | 20 | 페이지당 항목 수 |
| totalElements | 전체건수 | Number | 1 | 137 | 조건에 맞는 전체 건수 |
| totalPages | 전체페이지수 | Number | 1 | 7 | 전체 페이지 수 |

---

## 6. 공통 HTTP 상태 코드

| 코드 | 의미 | 사용 예 |
| --- | --- | --- |
| 200 | 성공 | 조회, 수정 |
| 201 | 생성됨 | 회원가입, 게시글 작성 |
| 204 | 성공(응답 본문 없음) | 삭제, 로그아웃 |
| 400 | 요청 값 오류 | 유효성 검증 실패 |
| 401 | 인증 실패 | 토큰 없음 / 만료 |
| 403 | 권한 없음 | 남의 글 수정 시도 |
| 404 | 리소스 없음 | 없는 정책 ID 조회 |
| 409 | 중복 · 충돌 | 아이디 중복 |
| 500 | 서버 내부 오류 | 예기치 못한 예외 |
| 503 | 일시적 사용 불가 | LLM 호출 실패 |

---

## 7. 공통 오류 코드

| errorCode | HTTP | 의미 |
| --- | --- | --- |
| INVALID_INPUT | 400 | 요청 값 유효성 검증 실패 |
| INVALID_CREDENTIALS | 401 | 아이디 또는 비밀번호 불일치 |
| TOKEN_EXPIRED | 401 | Access Token 만료 |
| INVALID_TOKEN | 401 | 위조되었거나 형식이 잘못된 토큰 |
| ACCESS_DENIED | 403 | 권한 없음 |
| NOT_POST_OWNER | 403 | 게시글 작성자가 아님 |
| USER_ID_DUPLICATED | 409 | 이미 사용 중인 아이디 |
| NICKNAME_DUPLICATED | 409 | 이미 사용 중인 닉네임 |
| POLICY_NOT_FOUND | 404 | 정책 없음 |
| PUBLIC_HOUSING_NOT_FOUND | 404 | 공공임대 공고 없음 |
| POST_NOT_FOUND | 404 | 게시글 없음 |
| COMMENT_NOT_FOUND | 404 | 댓글 없음 |
| TRANSACTION_NOT_FOUND | 404 | 실거래 내역 없음 |
| AI_SUMMARY_FAILED | 503 | LLM 호출 실패 (화면은 원문만 표시) |

---

## 8. 공통 Enum

| Enum | 값 |
| --- | --- |
| UserStatus | `ACTIVE`, `DELETED` |
| Job | `STUDENT`, `EMPLOYEE`, `SELF_EMPLOYED`, `JOB_SEEKER`, `ETC` |
| AgeRange | `AGE_20S_EARLY`, `AGE_20S_LATE`, `AGE_30S_EARLY`, `AGE_30S_LATE`, `ETC` |
| MaritalStatus | `SINGLE`, `MARRIED` |
| SalaryRange | `UNDER_2000`, `RANGE_2000_3000`, `RANGE_3000_4000`, `RANGE_4000_5000`, `RANGE_5000_7000`, `OVER_7000` |
| PolicyCategory | `HOUSING`, `LOAN`, `PUBLIC_HOUSING`, `SUPPLY` |
| PostCategory | `FREE`, `LOAN`, `INFO`, `QUESTION` |
| HousingType | `HAPPY_HOUSE`, `PURCHASE_RENTAL`, `JEONSE_RENTAL`, `NATIONAL_RENTAL` |
| DealType | `SALE`, `JEONSE`, `MONTHLY` |
| CrawlJobStatus | `PENDING`, `RUNNING`, `SUCCESS`, `FAILED` |

> `AgeRange` · `MaritalStatus` · `Job` 은 회원가입 온보딩 6단계(와이어프레임 03)의 선택지와 1:1로 대응합니다.

---

## 9. AI 요약 (정책 상세 화면 전용)

와이어프레임에서 AI 가 쓰이는 곳은 **07 정책상세 화면의 AI 요약 박스 하나뿐**입니다. Spring AI 로 처리합니다.

- **캐싱 필수**: 정책당 1회 생성 후 DB(`policy_ai_summaries`)에 저장하고 재사용합니다. 정책 원문이 갱신되면(`source_hash` 불일치) 무효화합니다.
- **면책 문구 필수**: 응답에 `disclaimer` 를 포함하며, 화면에 **반드시 노출**합니다.
  > "AI가 생성한 요약입니다. 실제 신청 전 반드시 원문을 확인하세요."
- **실패해도 화면은 살아야 합니다**: LLM 호출이 실패하면 503 `AI_SUMMARY_FAILED` 를 반환하고, 화면은 **요약 영역만 숨긴 채 정책 원문을 정상 표시**합니다. AI 실패가 화면 전체 실패로 이어지면 안 됩니다.

---

## 10. 이 문서에 없는 것 (의도적으로 제외)

와이어프레임에 화면이 없어 **명세하지 않은** 기능입니다. 필요해지면 화면부터 그리고 이 문서를 수정하세요.

| 제외한 기능 | 이유 |
| --- | --- |
| 계약서 위험분석 (OCR + LLM) | 와이어프레임에 화면 없음 |
| 관리자 페이지 (유저 제재, 게시글 모더레이션) | 와이어프레임에 화면 없음 |
| 데이터 버전 관리 · 롤백 | 관리자 기능이므로 제외 |
| 감사 로그 | 관리자 기능이므로 제외 |
| 크롤링 수동 트리거 · Job 관리 API | 크롤러는 배치로만 동작 |
| 소셜 로그인 | 와이어프레임 02 는 이메일/비밀번호만 |

### ⚠️ 화면은 없는데 버튼은 있는 것 (팀 논의 필요)

와이어프레임 08 커뮤니티 화면에 **버튼은 그려져 있으나 이동할 화면이 없는** 항목입니다. API 는 명세했으나 화면 설계가 필요합니다.

| 버튼 | 필요한 화면 | 관련 오퍼레이션 |
| --- | --- | --- |
| 「글쓰기」 | 게시글 작성 화면 | 22 |
| 게시글 제목 링크 | 게시글 상세 화면 | 23 |
| 「내 대출 한도 계산하기」 | 대출 한도 계산기 화면 | 32 |
