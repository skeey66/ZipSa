-- ============================================================
--  관리자 권한과 연령대 enum 정리 (명세 v4 §1.3)
--
--  · users.role 추가 — 기본 USER. 첫 ADMIN 은 API 가 아니라 DB 에서 직접 지정한다.
--    (관리자 승격 API 를 두면 그 자체가 권한 상승 경로가 된다)
--  · AgeRange 를 명세에 맞춘다. 기존 ETC 는 AGE_40S_OVER 로 옮긴다.
--  · UserStatus 에 SUSPENDED 가 추가되지만 컬럼은 varchar 라 DDL 변경은 없다.
-- ============================================================

ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';

CREATE INDEX idx_users_role_status ON users (role, status);

-- 명세의 AgeRange 는 AGE_10S / AGE_40S_OVER 를 포함한다. 기존 ETC 를 흡수한다.
UPDATE users SET age_range = 'AGE_40S_OVER' WHERE age_range = 'ETC';

COMMENT ON COLUMN users.role IS 'USER | ADMIN. 관리자 지정은 DB 에서 직접 UPDATE';
