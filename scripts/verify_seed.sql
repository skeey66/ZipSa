-- 시드 데이터에 심어둔 패턴이 실제로 복원되는지 확인합니다.
-- 「나와 비슷한 프로필의 사람들이 가장 많이 본 정책 5개」 = 협업 필터링의 최소 형태.
--
--   psql ... -f scripts/verify_seed.sql

WITH cohort AS (
  SELECT id AS user_id,
         CASE
           WHEN marital_status = 'MARRIED'                        THEN 'C 30대·기혼'
           WHEN job IN ('STUDENT','JOB_SEEKER')                   THEN 'A 20대초·학생/취준'
           WHEN job IN ('SELF_EMPLOYED','ETC')                    THEN 'D 30대·자영업'
           ELSE                                                        'B 20대후·직장인'
         END AS grp
  FROM users WHERE login_id LIKE 'seed_user_%'
),
ranked AS (
  SELECT c.grp, p.title, count(*) AS views,
         row_number() OVER (PARTITION BY c.grp ORDER BY count(*) DESC) AS rn
  FROM policy_views v
  JOIN cohort   c ON c.user_id = v.user_id
  JOIN policies p ON p.id = v.policy_id
  GROUP BY c.grp, p.title
)
SELECT grp AS "프로필 군집", rn AS "순위", title AS "정책", views AS "조회수"
FROM ranked WHERE rn <= 5 ORDER BY grp, rn;
