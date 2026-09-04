-- ============================================================
--  시드 회원의 지역 분포를 실제 인구 비율에 가깝게 조정
--
--  V12 는 id % 17 로 균등 배분해서 지역마다 9~10명이 됐다.
--  두 가지가 곤란하다.
--    ① 실제 청년 인구의 절반 이상이 수도권인데 전혀 반영되지 않는다.
--    ② 지역별 통계(예: "부산 청년들의 평균 대출 한도")를 내면 표본이 9명이라
--       한 사람 값이 평균을 흔든다. 화면에 숫자를 못 내보낸다.
--
--  ⚠️ 시드 계정(seed_user_%)만 건드린다. 실제 가입 계정은 본인이 고른 값을 유지한다.
-- ============================================================

UPDATE users
SET region = CASE
    WHEN r <  30 THEN '서울'   -- 30%
    WHEN r <  55 THEN '경기'   -- 25%
    WHEN r <  63 THEN '인천'   --  8%
    WHEN r <  70 THEN '부산'   --  7%
    WHEN r <  75 THEN '대구'   --  5%
    WHEN r <  80 THEN '광주'   --  5%
    WHEN r <  84 THEN '대전'   --  4%
    WHEN r <  88 THEN '경남'   --  4%
    WHEN r <  91 THEN '충남'   --  3%
    WHEN r <  94 THEN '경북'   --  3%
    WHEN r <  96 THEN '전북'   --  2%
    WHEN r <  98 THEN '전남'   --  2%
    WHEN r <  99 THEN '충북'
    WHEN r < 100 THEN '강원'
    ELSE '제주'
END
FROM (SELECT id AS uid, (id * 37) % 101 AS r FROM users WHERE login_id LIKE 'seed_user_%') s
WHERE users.id = s.uid;
