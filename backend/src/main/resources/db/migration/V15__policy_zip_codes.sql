-- ============================================================
--  정책 대상 지역 코드
--
--  온통청년 공식 API 가 zipCd 로 대상 법정동코드 목록을 준다.
--  ("11110,11140,11170,…" 또는 전국이면 250개 전부)
--
--  기존에는 기관명 문자열("부산광역시 주택건축국 주택정책과")에서 시·도를 긁어냈다.
--  "주택과" 처럼 지역이 안 들어간 값도 많아 매칭이 샜다.
--  코드로 비교하면 그런 문제가 없다.
--
--  회원의 region 은 시·도명(서울/부산)이므로 앞 2자리로 비교한다.
-- ============================================================

ALTER TABLE policies
    ADD COLUMN zip_codes  TEXT,
    ADD COLUMN sido_codes VARCHAR(120);

CREATE INDEX idx_policies_sido_codes ON policies (sido_codes);

COMMENT ON COLUMN policies.zip_codes  IS '대상 법정동코드 전체 목록(원본 zipCd)';
COMMENT ON COLUMN policies.sido_codes IS '대상 시·도코드를 중복 없이 모은 것. 예 "11,26,41". 전국이면 ALL';
