# ZipSa 크롤러

정책 · 공공임대 모집공고 · 실거래가를 수집해 **DB 에 직접 적재**합니다.
HTTP API 를 제공하지 않습니다. 배치로만 동작합니다.

## 실행

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

cp ../.env.example ../.env   # 최초 1회, 값 채우기
python -m zipsa_crawler.main --target policy
```

## 원칙

- **판단하지 않습니다.** 수집·정규화·적재까지만 하고, 추천·매칭·통계는 전부 Spring 이 합니다.
- **스키마는 백엔드와 공유합니다.** 컬럼을 바꾸려면 백엔드 담당자와 합의 후
  `backend/src/main/resources/db/migration/V2__*.sql` 을 추가하고 `docs/DB.dbml` 도 함께 고칩니다.
- **`external_id` 로 upsert** 합니다. 같은 공고를 두 번 넣지 않기 위해서입니다.
- **robots.txt 와 요청 간격을 지킵니다.** 과도한 요청은 팀 전체가 차단당하는 결과로 돌아옵니다.
