-- ============================================================
--  정책 조회 이력
--
--  「나와 비슷한 사람이 많이 본 정책」 추천의 원천입니다.
--  기존 policy_bookmarks 는 "찜"이라 표본이 너무 적어서
--  행동 기반 추천에는 조회 로그가 따로 필요합니다.
--
--  ⚠️ 이미 적용된 V1 은 수정하지 않습니다. 변경은 항상 새 파일로.
-- ============================================================

CREATE TABLE policy_views (
    id        BIGSERIAL PRIMARY KEY,
    user_id   BIGINT    NOT NULL REFERENCES users (id),
    policy_id BIGINT    NOT NULL REFERENCES policies (id) ON DELETE CASCADE,
    viewed_at TIMESTAMP NOT NULL DEFAULT now()
);

-- 「이 사용자가 본 것」 조회용
CREATE INDEX idx_policy_views_user_viewed ON policy_views (user_id, viewed_at DESC);
-- 「이 정책을 본 사람들」 역방향 조회용 — 협업 필터링에서 이쪽을 훨씬 많이 씁니다
CREATE INDEX idx_policy_views_policy      ON policy_views (policy_id);

COMMENT ON TABLE policy_views IS '정책 상세 조회 로그. 행동 기반 추천(비슷한 프로필이 많이 본 정책)의 입력값';
