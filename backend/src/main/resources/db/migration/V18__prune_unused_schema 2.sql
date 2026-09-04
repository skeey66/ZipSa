-- ============================================================
--  V18 — 쓰이지 않는 테이블·컬럼 정리와 FK 인덱스 보강
--
--  V1 에서 "나중에 쓰겠지" 하고 만든 뒤 아무도 쓰지 않은 것들을 걷어냅니다.
--  전부 행이 0건이라 삭제되는 데이터는 없습니다.
-- ============================================================

-- ─────────────── 1. 사용처가 없는 테이블 ───────────────
--
-- 셋 다 행 0건이고 Java 엔티티조차 없습니다. 스키마가 코드에 없는 기능을
-- 약속하고 있으면, 다음 사람이 "이미 있는 기능"으로 오해합니다.

-- AI 요약을 DB 캐시로 두려던 설계였으나, AiInsightService 의 메모리 LRU
-- 캐시로 바뀌면서 아무도 읽고 쓰지 않게 되었습니다.
DROP TABLE IF EXISTS policy_ai_summaries;

-- 대출 예측은 LoanPredictionService.estimateBase() 가 매번 계산합니다.
-- 같은 입력이면 항상 같은 값이 나오는 결정적 계산이라, 저장해도 보존되는
-- 사실이 없습니다(캐시일 뿐 기록이 아님). 회원이 직접 입력한 실제 결과는
-- loan_actuals 에 남으므로 그쪽만 있으면 됩니다.
DROP TABLE IF EXISTS loan_estimates;

-- 공공임대 찜 기능이 와이어프레임에 없습니다.
DROP TABLE IF EXISTS public_housing_bookmarks;

-- ─────────────── 2. 채울 수 없는 컬럼 ───────────────
--
-- public_housings 는 LH「분양임대공고문」목록 API 가 원천인데, 이 API 는
-- 공고명·유형·지역·모집기간·링크까지만 줍니다. 보증금·세대수 같은 조건은
-- 공고문 PDF 안에 있어서 목록 API 로는 얻을 수 없습니다.
-- 349건을 수집하는 동안 이 컬럼들은 한 번도 채워지지 않았습니다(전부 NULL).
--
-- 같은 사실이 필요하면 public_housing_complexes(마이홈포털 단지정보)에
-- 이미 들어 있습니다. 화면도 그쪽을 씁니다.
ALTER TABLE public_housings
    DROP COLUMN IF EXISTS deposit,
    DROP COLUMN IF EXISTS monthly_rent,
    DROP COLUMN IF EXISTS total_units,
    DROP COLUMN IF EXISTS exclusive_area,
    DROP COLUMN IF EXISTS eligibility,
    DROP COLUMN IF EXISTS address;

-- ─────────────── 3. FK 인덱스 ───────────────
--
-- PostgreSQL 은 FK 를 걸어도 자식 쪽 인덱스를 자동으로 만들지 않습니다.
-- 「이 회원이 쓴 댓글」처럼 자식에서 부모로 거슬러 세는 조회가 풀스캔이 됩니다.
CREATE INDEX idx_comments_user_id           ON comments (user_id);
CREATE INDEX idx_post_likes_user_id         ON post_likes (user_id);
CREATE INDEX idx_policy_bookmarks_policy_id ON policy_bookmarks (policy_id);
CREATE INDEX idx_loan_actuals_user_id       ON loan_actuals (user_id);

-- crawl_job_id 5개(policies·news·public_housings·public_housing_complexes·
-- real_estate_transactions)에는 일부러 인덱스를 만들지 않습니다.
-- 이 컬럼으로 조회하는 코드가 없고, 전부 대량 INSERT 대상 테이블이라
-- 인덱스를 붙이면 적재만 느려집니다. 조회 API 가 생기면 그때 추가합니다.

-- ─────────────── 4. 금액 단위 ───────────────
--
-- bigint 에 deposit/amount 라는 이름이라 컬럼명만 보면 단위를 알 수 없습니다.
-- 만원과 원을 섞으면 10,000배 틀리므로 DB 주석으로 못을 박아 둡니다.
-- (컬럼명에 단위를 넣는 편이 확실하지만 API 응답 필드명까지 바뀝니다.)
COMMENT ON COLUMN real_estate_transactions.deal_amount  IS '만원 단위. 매매는 거래금액, 전월세는 보증금';
COMMENT ON COLUMN real_estate_transactions.monthly_rent IS '만원 단위. 매매는 NULL, 전세는 0, 월세는 월세액';
COMMENT ON COLUMN public_housing_complexes.deposit      IS '원 단위(bassRentGtn). 실거래가의 만원 단위와 다르다';
COMMENT ON COLUMN public_housing_complexes.monthly_rent IS '원 단위(bassMtRntchrg)';
COMMENT ON COLUMN loan_actuals.actual_limit             IS '원 단위. 반려(REJECTED)면 NULL';
