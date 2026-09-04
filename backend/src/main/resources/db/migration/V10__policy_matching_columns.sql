-- ============================================================
--  맞춤 정책 추천(POLICY-004)을 위한 조건 컬럼
--
--  기존 target_age_range 는 varchar 라 "18~45" 같은 문자열만 담을 수 있고,
--  회원의 AgeRange enum 과 비교할 수가 없다. 추천이 "연관율" 을 계산하려면
--  숫자 범위가 필요하다. 원문 표기는 target_* 컬럼에 그대로 남긴다.
-- ============================================================

ALTER TABLE policies
    ADD COLUMN target_min_age    INT,
    ADD COLUMN target_max_age    INT,
    ADD COLUMN earn_min_amt      BIGINT,
    ADD COLUMN earn_max_amt      BIGINT,
    ADD COLUMN marital_condition VARCHAR(60),
    ADD COLUMN keyword           VARCHAR(100);

CREATE INDEX idx_policies_target_age ON policies (target_min_age, target_max_age);

COMMENT ON COLUMN policies.target_min_age    IS '지원 대상 최소 나이. 회원 나이대와 겹치는지 비교';
COMMENT ON COLUMN policies.target_max_age    IS '지원 대상 최대 나이';
COMMENT ON COLUMN policies.earn_min_amt      IS '소득 조건 하한(만원). 원문은 target_salary_range';
COMMENT ON COLUMN policies.earn_max_amt      IS '소득 조건 상한(만원)';
COMMENT ON COLUMN policies.marital_condition IS '결혼 조건 원문(제한없음|기혼|미혼). 신혼부부 정책 판별에 쓴다';
COMMENT ON COLUMN policies.keyword           IS '온통청년 정책 키워드(주거지원 등)';
