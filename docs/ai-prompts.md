# ZipSa AI 프롬프트 명세

> 코드에서 그대로 옮긴 문서입니다 — 시스템/유저 프롬프트는 `ai/LlmInsight.java` ·
> `loan/LoanReportWriter.java` 원문 그대로이고, JSON 스키마는 실제로 그 코드를 실행해
> `BeanOutputConverter.getFormat()` 결과를 받은 것입니다. 손으로 지어낸 문장은 없습니다.
>
> 프롬프트를 고치면 이 문서도 같이 고치세요. `Draft` 레코드의 필드를 바꾸면 스키마도
> 바뀌므로, 아래 「검증 방법」대로 다시 뽑아서 갱신하면 됩니다.

## 0. 한눈에 보기

세 곳에서 OpenAI(`gpt-4o-mini`)를 Spring AI `ChatClient`로 부릅니다. 셋 다 같은 원칙입니다 —
**숫자와 결론은 코드가 계산해서 프롬프트에 넣고, LLM은 그것을 사람이 읽을 문장으로 풀어쓰는 역할만** 합니다.
LLM이 실패하거나 키가 없으면 같은 자리를 규칙 기반 문장이 채우고, `aiGenerated: false`로 그 사실이 화면까지 전달됩니다.

| # | 기능 | 엔드포인트 | 호출 코드 | 실패 시 폴백 |
| --- | --- | --- | --- | --- |
| ① | 정책 AI 인사이트 | `GET /api/ai/policies/{policyId}` | `LlmInsight.forPolicy()` | `RuleBasedInsight.forPolicy()` |
| ② | 뉴스 AI 인사이트 | `GET /api/ai/news/{newsId}` | `LlmInsight.forNews()` | `RuleBasedInsight.forNews()` |
| ③ | 대출 분석레포트 | `GET /api/loans/prediction` | `LoanReportWriter.write()` | `LoanPredictionService.buildReport()` |

**모델 설정** (`backend/src/main/resources/application.yml`)

```yaml
spring:
  ai:
    openai:
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
      chat:
        options:
          model: ${OPENAI_MODEL:gpt-4o-mini}
          temperature: 0.2
```

`response_format`(OpenAI 네이티브 Structured Outputs / JSON mode)은 **설정하지 않았습니다.** 대신 Spring AI의
`ChatClient.entity(Class)`가 자동으로 붙이는 **프롬프트 기반 JSON 지시문**(`BeanOutputConverter`)을 씁니다 — 1.2절 참고.

---

## 1. 공통 메커니즘 — `.entity(Draft.class)`는 실제로 무엇을 하나

`LlmInsight`와 `LoanReportWriter`는 응답을 자바 레코드로 직접 받습니다.

```java
Draft draft = chatClient.prompt()
        .user(u -> u.text("...").param(...))
        .call()
        .entity(Draft.class);
```

### 1.1 우리가 작성하는 것 — 시스템 프롬프트 + 유저 프롬프트 템플릿

시스템 프롬프트는 `ChatClient.Builder.defaultSystem(SYSTEM)`으로 한 번 고정됩니다.
유저 프롬프트는 `st`(String Template) 문법의 `{param}` 자리표시자를 코드가 채웁니다. 이 두 문자열이 우리가 실제로 짠 프롬프트의 전부입니다.

### 1.2 Spring AI가 자동으로 덧붙이는 것 — JSON 지시문

`.entity(Draft.class)`를 호출하면 Spring AI가 `Draft` 레코드에서 **JSON 스키마를 생성**하고, 아래 고정 문구 + 스키마를
**우리가 쓴 유저 프롬프트 끝에 자동으로 이어붙여** 전송합니다. 우리는 이 문구를 작성하지 않습니다 — 라이브러리가 만듭니다.

```
Your response should be in JSON format.
Do not include any explanations, only provide a RFC8259 compliant JSON response following this format without deviation.
Do not include markdown code blocks in your response.
Remove the ```json markdown from the output.
Here is the JSON Schema instance your output must adhere to:
```{ ... 아래 2절 스키마 ... }```
```

즉 OpenAI 서버 입장에서 "시스템: 페르소나", "유저: 우리 템플릿 + 이 지시문 + 스키마" 두 메시지만 받습니다.
OpenAI의 네이티브 `response_format: json_schema`(강제 모드)가 아니라, **모델에게 텍스트로 부탁하는 방식**이라는 뜻입니다.
그래서 코드가 응답을 파싱할 때 실패할 가능성을 항상 열어 두고, 실패하면 예외를 던져 규칙 기반으로 떨어뜨립니다
(`LlmInsight.toResponse()`의 빈 응답 체크, `LoanReportWriter.write()`의 `try/catch`).

### 1.3 검증 방법 — 이 문서의 스키마가 실측인 이유

리포 어디에도 스키마를 손으로 적어둔 파일이 없습니다. 아래처럼 실제 `BeanOutputConverter`를 돌려서 얻습니다.

```java
// backend/src/test/java/.../PromptSchemaDump.java (임시 테스트 — 확인 후 삭제)
record Draft(List<String> summary, String verdict, List<String> reasons, List<String> nextSteps) {}

var converter = new org.springframework.ai.converter.BeanOutputConverter<>(Draft.class);
System.out.println(converter.getFormat());
```

```bash
cd backend && ./gradlew test --tests "com.zipsa.scratch.PromptSchemaDump" -i
```

---

## 2. ① 정책 AI 인사이트 — `LlmInsight.forPolicy()`

`GET /api/ai/policies/{policyId}` → `AiInsightController` → `AiInsightService.forPolicy()` →
(`AiAvailability` 확인 + 캐시 미스) → **`LlmInsight.forPolicy()`**

### 2.1 시스템 프롬프트 (원문 그대로)

```
너는 한국의 청년 주거 서비스 'ZIP 보금자리'의 정책 안내 도우미다.
사용자에게 존댓말로, 군더더기 없이 사실만 전달한다.

반드시 지킬 것
- 제공된 사실만 사용한다. 없는 금액·날짜·조건을 지어내지 않는다.
- 이미 계산된 판정(신청 가능 여부, 남은 일수)을 그대로 따른다. 다시 계산하지 않는다.
- summary 는 3문장. 각 문장은 한 줄로 끝낸다.
- verdict(한 줄 결론)는 이미 확정돼 있다. 바꾸거나 뒤집지 않는다.
- reasons 는 판정의 근거를 2~4개.
- nextSteps 는 사용자가 지금 할 수 있는 구체적인 행동을 1~3개.
- 과장하거나 권유하지 않는다. "꼭 신청하세요" 같은 표현은 쓰지 않는다.
```

이 시스템 프롬프트가 `verdict`를 "이미 확정돼 있다"고 못 박는 이유는 `LlmInsight.toResponse()`의 구현과 짝을 이룹니다 —
**최종 응답의 `verdict`는 LLM이 쓴 문장이 아니라 `RuleBasedInsight`가 계산한 `Verdict.headline()`을 그대로 씁니다.**
코드 주석에 남은 실제 사고 사례: LLM이 "신청해 볼 만한 정책입니다"라고 쓰고 `tone`은 코드가 정한 `caution`이 남아,
화면에 ⚠️ 아이콘과 낙관적인 문장이 동시에 뜬 적이 있어 이렇게 고정했습니다.

### 2.2 유저 프롬프트 템플릿 (원문 그대로)

```
아래 청년 정책을 요약하고, 이 회원에게 어떻게 적용되는지 설명해 줘.

[정책]
제목: {title}
분류: {category}
대상 지역: {region}
주관: {issuer}
지원 나이: {age}
소득 조건: {salary}
신청 기간: {period}
신청 방법: {method}
내용:
{content}

[회원]
{profile}

[이미 확정된 판정 — 이대로 따를 것]
결론: {verdict}
판정 근거: {facts}
```

| 자리표시자 | 채우는 코드 | 비고 |
| --- | --- | --- |
| `{title} {category} {issuer}` | `Policy` 필드 그대로 | |
| `{region}` | `RegionCodes.displayOf(p.getSidoCodes())`, 없으면 `"정보 없음"` | |
| `{age} {salary}` | 없으면 `"제한 없음"` | |
| `{period}` | `LoanPredictionService`가 아니라 `LlmInsight.period()` — `"시작 ~ 종료 (마감까지 N일)"` 형태로 코드가 직접 계산 | 날짜 계산을 LLM에 맡기지 않으려는 것 |
| `{content}` | 정책 본문, 1,800자에서 자름 | 토큰 절약 |
| `{profile}` | `"20대 후반 · 직장인 · 연소득 3~4천만원 · 미혼 · 거주 서울 마포구"` 형태 | `LlmInsight.profile()` |
| `{verdict} {facts}` | `RuleBasedInsight`가 먼저 계산한 `Verdict.headline()` / `Verdict.facts()` | **LLM에 건네는 입력이지 LLM이 만드는 값이 아님** |

### 2.3 실제로 전송되는 프롬프트 — 채워 넣은 예시

정책: "청년 월세 특별지원"(주거지원, 서울, 서울특별시 주관, 만 19~34세, 연 5천만원 이하, 2026-09-20 마감,
복지로 온라인 신청) · 회원: 20대 후반 · 직장인 · 연소득 3~4천만원 · 미혼 · 서울 마포구 거주.
`RuleBasedInsight`가 먼저 계산한 판정: 나이·지역 조건 모두 통과(`matched=2`) → `"조건이 맞습니다. 마감까지 17일 남았습니다."` / `tone=good`.

```
아래 청년 정책을 요약하고, 이 회원에게 어떻게 적용되는지 설명해 줘.

[정책]
제목: 청년 월세 특별지원
분류: 주거지원
대상 지역: 서울
주관: 서울특별시
지원 나이: 만 19~34세
소득 조건: 연 5천만원 이하
신청 기간: 2026-08-01 ~ 2026-09-20 (마감까지 17일)
신청 방법: 복지로 온라인 신청
내용:
서울시에 거주하는 무주택 청년 1인가구에게 월세를 최대 20만원씩 12개월간 지원합니다. 기준중위소득 150%
이하이며 임차보증금 5천만원, 월세 60만원 이하 주택에 거주해야 합니다. …

[회원]
20대 후반 · 직장인 · 연소득 3~4천만원 · 미혼 · 거주 서울 마포구

[이미 확정된 판정 — 이대로 따를 것]
결론: 조건이 맞습니다. 마감까지 17일 남았습니다.
판정 근거: 20대 후반은 이 정책의 나이 조건(만 19~34세)에 들어갑니다.
- 거주 지역(서울 마포구)이 이 정책의 대상 지역과 일치합니다.
```

*(실제 전송 시 이 아래에 1.2절의 JSON 지시문 + 2.4절 스키마가 Spring AI에 의해 자동으로 이어붙습니다.)*

### 2.4 모델이 지켜야 하는 JSON 스키마 (실측)

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "properties": {
    "nextSteps": { "type": "array", "items": { "type": "string" } },
    "reasons":   { "type": "array", "items": { "type": "string" } },
    "summary":   { "type": "array", "items": { "type": "string" } },
    "verdict":   { "type": "string" }
  },
  "additionalProperties": false
}
```

### 2.5 모델 응답 예시 — `LlmInsight.Draft`

```json
{
  "summary": [
    "청년 월세 특별지원은 만 19~34세 무주택 청년에게 월세를 최대 20만원씩 12개월 지원하는 사업입니다.",
    "서울시 거주자 대상이며 기준중위소득 150% 이하, 임차보증금 5천만원 이하 주택이 조건입니다.",
    "신청은 복지로 홈페이지에서 온라인으로 진행하며 마감일까지 서류 제출을 완료해야 합니다."
  ],
  "verdict": "이 조건이면 신청해 볼 수 있는 정책입니다.",
  "reasons": [
    "20대 후반은 이 정책의 나이 조건(만 19~34세)에 들어갑니다.",
    "거주 지역(서울 마포구)이 이 정책의 대상 지역과 일치합니다.",
    "소득 조건(연 5천만원 이하)도 충족하는 범위입니다."
  ],
  "nextSteps": [
    "복지로 홈페이지에서 신청서와 임대차계약서를 준비하세요.",
    "마감까지 17일 남았으니 서류부터 먼저 확인하세요."
  ]
}
```

`verdict` 필드는 모델이 위처럼 다른 문장을 써서 보내더라도 **최종 응답에는 쓰이지 않습니다.** `toResponse()`가 코드가 계산한
`Verdict.headline()`으로 덮어씁니다 — 2.1절에서 설명한 사고를 막기 위한 장치입니다. `reasons`는 모델이 비어 있지 않게 보내면
그대로 쓰고, 비어 있으면 `Verdict.facts()`로 대체합니다.

### 2.6 최종 API 응답 — `GET /api/ai/policies/{policyId}`

```json
{
  "success": true,
  "data": {
    "summary": [
      "청년 월세 특별지원은 만 19~34세 무주택 청년에게 월세를 최대 20만원씩 12개월 지원하는 사업입니다.",
      "서울시 거주자 대상이며 기준중위소득 150% 이하, 임차보증금 5천만원 이하 주택이 조건입니다.",
      "신청은 복지로 홈페이지에서 온라인으로 진행하며 마감일까지 서류 제출을 완료해야 합니다."
    ],
    "application": {
      "verdict": "조건이 맞습니다. 마감까지 17일 남았습니다.",
      "reasons": [
        "20대 후반은 이 정책의 나이 조건(만 19~34세)에 들어갑니다.",
        "거주 지역(서울 마포구)이 이 정책의 대상 지역과 일치합니다.",
        "소득 조건(연 5천만원 이하)도 충족하는 범위입니다."
      ],
      "nextSteps": [
        "복지로 홈페이지에서 신청서와 임대차계약서를 준비하세요.",
        "마감까지 17일 남았으니 서류부터 먼저 확인하세요."
      ],
      "tone": "good"
    },
    "aiGenerated": true
  },
  "error": null
}
```

`verdict`가 2.5절 모델 원문이 아니라 2.3절의 "확정된 판정" 그대로인 것에 주목하세요 — `toResponse()`가 덮어쓴 결과입니다.

---

## 3. ② 뉴스 AI 인사이트 — `LlmInsight.forNews()`

`GET /api/ai/news/{newsId}` → `AiInsightService.forNews()` → **`LlmInsight.forNews()`**. 시스템 프롬프트는 정책과 동일(2.1절).
`Verdict`는 `RuleBasedInsight.applyNews()`가 기사 본문의 키워드(전세·대출·공급·세제)로 미리 판정합니다.

### 3.1 유저 프롬프트 템플릿 (원문 그대로)

```
아래 기사를 3문장으로 요약하고, 이 회원의 주거 상황에 어떤 의미인지 설명해 줘.

[기사]
제목: {title}
언론사: {press}
본문:
{content}

[회원]
{profile}

[참고 판정]
{verdict}
```

정책 템플릿과 달리 `facts`(근거 목록) 없이 `verdict` 한 줄만 넘깁니다 — 뉴스는 규칙 기반 판정이 "대출 조건에 영향이 있을 수
있는 소식입니다" 같은 주제 분류 수준이라, 정책만큼 세밀한 근거가 없기 때문입니다. 본문은 2,500자에서 자릅니다.

### 3.2 채워 넣은 예시

기사: "한국은행, 기준금리 0.25%p 인하" · 회원: 30대 초반 · 직장인 · 연소득 4~5천만원.
`RuleBasedInsight`가 본문에서 "금리·대출" 키워드를 감지 → `verdict = "대출 조건에 영향이 있을 수 있는 소식입니다."`

```
아래 기사를 3문장으로 요약하고, 이 회원의 주거 상황에 어떤 의미인지 설명해 줘.

[기사]
제목: 한국은행, 기준금리 0.25%p 인하…시중은행 주담대 금리 하락 전망
언론사: 연합뉴스
본문:
한국은행 금융통화위원회는 3일 기준금리를 연 3.25%에서 3.00%로 0.25%포인트 인하했다. 이번 인하로
시중은행의 주택담보대출 금리도 순차적으로 낮아질 전망이다. 전문가들은 실수요자의 대출 부담이 줄어들
것으로 내다봤다. …

[회원]
30대 초반 · 직장인 · 연소득 4~5천만원 · 미혼 · 거주 서울 강서구

[참고 판정]
대출 조건에 영향이 있을 수 있는 소식입니다.
```

### 3.3 스키마와 응답 — 정책과 동일 구조

스키마는 2.4절과 완전히 같습니다(`Draft` 레코드를 공유). 모델 응답 예시만 다릅니다.

```json
{
  "summary": [
    "한국은행이 기준금리를 3.25%에서 3.00%로 0.25%포인트 인하했습니다.",
    "이번 인하로 시중은행 주택담보대출 금리도 순차적으로 낮아질 전망입니다.",
    "실수요자의 대출 이자 부담이 다소 줄어들 것으로 예상됩니다."
  ],
  "verdict": "대출 조건에 영향이 있을 수 있는 소식입니다.",
  "reasons": [
    "30대 초반 · 직장인 · 연소득 4~5천만원 조건이면 대출 한도와 금리가 직접 영향을 받는 기사입니다."
  ],
  "nextSteps": [
    "「대출예측」에서 지금 조건의 은행별 예상 한도를 다시 확인해 보세요."
  ]
}
```

최종 응답 봉투는 2.6절과 같은 모양(`{summary, application:{verdict,reasons,nextSteps,tone}, aiGenerated}`)입니다.

---

## 4. ③ 대출 분석레포트 — `LoanReportWriter.write()`

`GET /api/loans/prediction` → `LoanPredictionService.predict()` → 은행별 예상 한도·금리를 **코드가 먼저 계산**
(`buildReport()`로 규칙 기반 폴백까지 만들어 둠) → **`LoanReportWriter.write(profile, banks, fallback)`**.

### 4.1 시스템 프롬프트 (원문 그대로)

```
너는 청년 주거 서비스의 대출 상담 도우미다. 존댓말로, 사실만 간결하게 전달한다.

반드시 지킬 것
- 제공된 숫자만 쓴다. 금액·금리·건수를 새로 만들지 않는다.
- 3~4문장. 각 문장은 독립적으로 읽히게 쓴다.
- 한도가 큰 곳과 금리가 낮은 곳이 다르면 그 차이를 짚어준다.
- 특정 은행을 권유하지 않는다. 판단 재료만 준다.
- "반드시", "무조건" 같은 단정적 표현을 쓰지 않는다.
```

### 4.2 유저 프롬프트 템플릿 (원문 그대로)

```
아래 회원의 조건에서 은행별 예상 대출 한도를 비교해 설명해 줘.

[회원] {profile}

[은행별 예상]
{table}

[요약] {headline}
```

`{table}`은 코드가 `String.format("- %s: 예상 한도 %,d원, 금리 연 %.2f%%, 표본 %d건", ...)`로 만든 줄바꿈 목록이고,
`{headline}`은 `buildReport()`가 만든 `"예상 한도 최저 ~ 최고"` 문자열입니다 — 둘 다 LLM이 아니라 코드가 계산한 숫자입니다.

### 4.3 채워 넣은 예시

회원: 20대 후반 · 직장인 · 연소득 3~4천만원 · 서울 마포구 거주. 은행 4곳 예상치는 코드가 이미 계산해 정렬해 둔 상태.

```
아래 회원의 조건에서 은행별 예상 대출 한도를 비교해 설명해 줘.

[회원] 20대 후반 · 직장인 · 연소득 3~4천만원 · 거주 서울 마포구

[은행별 예상]
- KB국민은행: 예상 한도 92,000,000원, 금리 연 3.42%, 표본 128건
- NH농협은행: 예상 한도 88,500,000원, 금리 연 3.51%, 표본 96건
- 하나은행: 예상 한도 85,000,000원, 금리 연 3.38%, 표본 74건
- 우리은행: 예상 한도 79,000,000원, 금리 연 3.60%, 표본 61건

[요약] 예상 한도 7,900만원 ~ 9,200만원
```

### 4.4 스키마 (실측)

`Draft`가 `LlmInsight.Draft`와 다른 레코드라 스키마도 다릅니다 — 필드가 `insights` 하나뿐입니다.

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "properties": {
    "insights": { "type": "array", "items": { "type": "string" } }
  },
  "additionalProperties": false
}
```

### 4.5 모델 응답 예시 — `LoanReportWriter.Draft`

```json
{
  "insights": [
    "20대 후반 · 직장인 · 연소득 3~4천만원 조건에서는 KB국민은행의 예상 한도가 9,200만원으로 가장 높습니다.",
    "다만 금리는 하나은행이 연 3.38%로 가장 낮습니다. 한도 8,500만원 정도로 충분하다면 이자 부담을 줄이는 쪽이 유리할 수 있습니다.",
    "은행 간 예상 한도 차이는 약 1,300만원입니다. 같은 조건이어도 심사 기준이 달라 두세 곳은 함께 문의해 보시는 편이 좋습니다."
  ]
}
```

### 4.6 최종 API 응답 — `GET /api/loans/prediction`의 `report` 필드

```json
{
  "success": true,
  "data": {
    "profile": { "nickname": "seed_user_042", "ageRange": "20대 후반", "job": "직장인",
                 "salaryRange": "3~4천만원", "maritalStatus": "미혼" },
    "buckets": ["반려", "0.5억 미만", "0.5~1억", "1~1.5억", "1.5~2억", "2억 이상"],
    "banks": [ /* … BankPrediction 4건 … */ ],
    "report": {
      "headline": "예상 한도 7,900만원 ~ 9,200만원",
      "insights": [
        "20대 후반 · 직장인 · 연소득 3~4천만원 조건에서는 KB국민은행의 예상 한도가 9,200만원으로 가장 높습니다.",
        "다만 금리는 하나은행이 연 3.38%로 가장 낮습니다. 한도 8,500만원 정도로 충분하다면 이자 부담을 줄이는 쪽이 유리할 수 있습니다.",
        "은행 간 예상 한도 차이는 약 1,300만원입니다. 같은 조건이어도 심사 기준이 달라 두세 곳은 함께 문의해 보시는 편이 좋습니다."
      ],
      "recommendedBank": "KB국민은행",
      "disclaimer": "회원 359건의 실제 승인 이력을 바탕으로 산출한 참고값입니다. 실제 심사 결과와 다를 수 있습니다.",
      "aiGenerated": true
    }
  },
  "error": null
}
```

`headline` · `recommendedBank` · `disclaimer`는 `insights`만 LLM 것으로 갈아 끼운 `fallback`(규칙 기반) 값 그대로입니다
(`LoanReportWriter.write()` 마지막 줄: `new AnalysisReport(fallback.headline(), draft.insights(), fallback.recommendedBank(), fallback.disclaimer(), true)`).
금액·은행 추천은 끝까지 코드가 정하고, LLM은 `insights` 문장 3~4개만 새로 씁니다.

---

## 5. 실패·미설정 시 — 세 곳 다 같은 규칙

| 상황 | 판정 위치 | 결과 |
| --- | --- | --- |
| `OPENAI_API_KEY` 없음 (`application.yml`의 `not-configured` 자리표시자) | `AiAvailability.isConfigured()` | LLM 호출 자체를 안 함 → 규칙 기반 그대로 |
| 키는 있는데 `ChatModel`/`ChatClient.Builder` 빈이 없음 | `ObjectProvider.getIfAvailable()` | 규칙 기반 |
| LLM이 빈 배열/빈 문자열 반환 | `Draft.summary()`가 비었는지 체크 (`LlmInsight`) / `insights()`가 비었는지 체크 (`LoanReportWriter`) | 규칙 기반 |
| 그 외 예외 (타임아웃, 401, JSON 파싱 실패 등) | `try/catch` → `log.warn` | 규칙 기반, 예외는 사용자에게 노출 안 함 |

세 상황 모두 응답의 `aiGenerated`가 `false`로 내려가고, 프론트는 이 값으로 화면에 「AI」 대신 「샘플」 배지를 붙입니다
(`ai/AiInsightResponse.java`, `loan/dto/LoanPredictionResponse.AnalysisReport` 주석 참고).
