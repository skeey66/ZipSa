-- ============================================================
--  대출 신청 결과 상태 (승인 / 반려)
--
--  화면 14 그래프에 「반려」 막대를 추가하기 위해 필요하다.
--  승인 금액만 모으면 "이 조건이면 얼마 받는다" 는 알 수 있지만
--  "이 조건이면 떨어질 수도 있다" 는 알 수 없다. 사용자가 정작 알고 싶은 건 후자다.
--
--  반려 건은 승인 금액이 없다. 0 을 넣으면 "0원 승인" 과 구분되지 않고
--  평균 계산에도 섞여 들어가므로 NULL 을 허용한다.
-- ============================================================

ALTER TABLE loan_actuals
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';

ALTER TABLE loan_actuals
    ALTER COLUMN actual_limit DROP NOT NULL;

CREATE INDEX idx_loan_actuals_bank_status ON loan_actuals (bank_name, status);

COMMENT ON COLUMN loan_actuals.status       IS 'APPROVED | REJECTED';
COMMENT ON COLUMN loan_actuals.actual_limit IS '원 단위. 반려(REJECTED)면 NULL';

-- 반려 건이 하나도 없으면 그래프의 「반려」 막대가 항상 0이라 의미가 없다.
-- 시드 데이터의 일부를 반려로 바꾼다. 소득이 낮거나 증빙이 어려운 조건일수록
-- 반려 비율이 높게 잡는다(균등 난수면 조건별 차이가 사라진다).
UPDATE loan_actuals la
SET status = 'REJECTED', actual_limit = NULL, actual_rate = NULL
FROM users u
WHERE u.id = la.user_id
  AND u.login_id LIKE 'seed_user_%'
  AND (la.id * 17) % 100 < CASE
        WHEN u.job IN ('JOB_SEEKER', 'STUDENT')                THEN 34
        WHEN u.salary_range IN ('UNDER_2000', 'RANGE_2000_3000') THEN 22
        WHEN u.job = 'SELF_EMPLOYED'                            THEN 20
        ELSE 8
      END;
