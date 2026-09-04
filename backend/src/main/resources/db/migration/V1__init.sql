-- ============================================================
--  ZipSa 초기 스키마
--  docs/schema.dbml 과 1:1로 대응합니다. 한쪽만 고치지 마세요.
--
--  ⚠️ Spring Boot 와 Python 크롤러가 함께 쓰는 스키마입니다.
--     컬럼 변경은 양쪽 담당자 합의 후 새 마이그레이션(V2__...)으로 추가하고,
--     이미 적용된 이 파일은 절대 수정하지 않습니다.
-- ============================================================

-- ─────────────── 1. 회원 · 인증 ───────────────

CREATE TABLE users (
    id             BIGSERIAL    PRIMARY KEY,
    login_id       VARCHAR(50)  NOT NULL UNIQUE,
    password       VARCHAR(255) NOT NULL,
    nickname       VARCHAR(30)  NOT NULL UNIQUE,
    age_range      VARCHAR(20),
    marital_status VARCHAR(20),
    job            VARCHAR(20),
    salary_range   VARCHAR(20),
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT now(),
    deleted_at     TIMESTAMP
);
COMMENT ON TABLE users IS '회원가입 온보딩 6단계(와이어프레임 03)에서 프로필 수집. 관리자 역할 없음';

CREATE TABLE refresh_tokens (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id),
    token      VARCHAR(512) NOT NULL UNIQUE,
    expires_at TIMESTAMP    NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user_id    ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

-- ─────────────── 7. 크롤링 운영 (FK 대상이므로 먼저 생성) ───────────────

CREATE TABLE crawl_jobs (
    id                BIGSERIAL   PRIMARY KEY,
    target            VARCHAR(20) NOT NULL,
    target_region     VARCHAR(50),
    target_year_month VARCHAR(7),
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    processed_count   INT         NOT NULL DEFAULT 0,
    error_message     TEXT,
    started_at        TIMESTAMP,
    finished_at       TIMESTAMP,
    created_at        TIMESTAMP   NOT NULL DEFAULT now()
);
COMMENT ON TABLE crawl_jobs IS '크롤러 실행 이력. 조회 API 없음(관리자 화면 없음) — 운영자는 DB 직접 조회';

-- ─────────────── 2. 정책 ───────────────

CREATE TABLE policies (
    id                  BIGSERIAL    PRIMARY KEY,
    external_id         VARCHAR(100) NOT NULL UNIQUE,
    title               VARCHAR(255) NOT NULL,
    content             TEXT,
    category            VARCHAR(20)  NOT NULL,
    region              VARCHAR(50),
    issuer              VARCHAR(100),
    target_job          VARCHAR(255),
    target_age_range    VARCHAR(255),
    target_salary_range VARCHAR(255),
    apply_start_date    DATE,
    apply_end_date      DATE,
    apply_method        TEXT,
    source_name         VARCHAR(100),
    source_url          VARCHAR(500) NOT NULL,
    crawled_at          TIMESTAMP    NOT NULL,
    crawl_job_id        BIGINT       REFERENCES crawl_jobs (id),
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_policies_category_region ON policies (category, region);
CREATE INDEX idx_policies_apply_end_date  ON policies (apply_end_date);
COMMENT ON COLUMN policies.external_id IS '원천 사이트 고유 식별자. 크롤러가 이 값으로 upsert';

CREATE TABLE policy_ai_summaries (
    id           BIGSERIAL   PRIMARY KEY,
    policy_id    BIGINT      NOT NULL UNIQUE REFERENCES policies (id) ON DELETE CASCADE,
    summary      TEXT        NOT NULL,
    model        VARCHAR(50),
    source_hash  VARCHAR(64),
    generated_at TIMESTAMP   NOT NULL DEFAULT now()
);
COMMENT ON TABLE policy_ai_summaries IS 'Spring AI 생성 결과 캐시. 정책당 1건. 없으면 화면은 원문만 표시';

CREATE TABLE policy_bookmarks (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT    NOT NULL REFERENCES users (id),
    policy_id  BIGINT    NOT NULL REFERENCES policies (id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_policy_bookmarks UNIQUE (user_id, policy_id)
);

-- ─────────────── 3. 공공임대 ───────────────

CREATE TABLE public_housings (
    id                 BIGSERIAL    PRIMARY KEY,
    external_id        VARCHAR(100) NOT NULL UNIQUE,
    name               VARCHAR(255) NOT NULL,
    housing_type       VARCHAR(30)  NOT NULL,
    region             VARCHAR(50),
    address            VARCHAR(255),
    recruit_start_date DATE         NOT NULL,
    recruit_end_date   DATE         NOT NULL,
    total_units        INT,
    deposit            BIGINT,
    monthly_rent       BIGINT,
    exclusive_area     DECIMAL(6, 2),
    eligibility        TEXT,
    apply_url          VARCHAR(500),
    source_url         VARCHAR(500) NOT NULL,
    crawled_at         TIMESTAMP    NOT NULL,
    crawl_job_id       BIGINT       REFERENCES crawl_jobs (id),
    created_at         TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_public_housings_region_type ON public_housings (region, housing_type);
CREATE INDEX idx_public_housings_recruit     ON public_housings (recruit_start_date, recruit_end_date);
COMMENT ON COLUMN public_housings.recruit_start_date IS '와이어프레임 10 캘린더의 점 표시 기준';

CREATE TABLE public_housing_bookmarks (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT    NOT NULL REFERENCES users (id),
    public_housing_id BIGINT    NOT NULL REFERENCES public_housings (id),
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_public_housing_bookmarks UNIQUE (user_id, public_housing_id)
);

-- ─────────────── 4. 커뮤니티 ───────────────

CREATE TABLE posts (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users (id),
    title         VARCHAR(200) NOT NULL,
    content       TEXT         NOT NULL,
    category      VARCHAR(20)  NOT NULL,
    view_count    INT          NOT NULL DEFAULT 0,
    like_count    INT          NOT NULL DEFAULT 0,
    comment_count INT          NOT NULL DEFAULT 0,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHED',
    deleted_at    TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_posts_category_created ON posts (category, created_at);
CREATE INDEX idx_posts_user_id          ON posts (user_id);

CREATE TABLE comments (
    id         BIGSERIAL   PRIMARY KEY,
    post_id    BIGINT      NOT NULL REFERENCES posts (id),
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    content    TEXT        NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    deleted_at TIMESTAMP,
    created_at TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at TIMESTAMP   NOT NULL DEFAULT now()
);
CREATE INDEX idx_comments_post_created ON comments (post_id, created_at);

CREATE TABLE post_likes (
    id         BIGSERIAL PRIMARY KEY,
    post_id    BIGINT    NOT NULL REFERENCES posts (id),
    user_id    BIGINT    NOT NULL REFERENCES users (id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_post_likes UNIQUE (post_id, user_id)
);

-- ─────────────── 5. 대출 한도 ───────────────

CREATE TABLE loan_estimates (
    id              BIGSERIAL     PRIMARY KEY,
    user_id         BIGINT        NOT NULL REFERENCES users (id),
    policy_id       BIGINT        NOT NULL REFERENCES policies (id),
    estimated_limit BIGINT        NOT NULL,
    estimated_rate  DECIMAL(4, 2),
    basis           TEXT,
    created_at      TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX idx_loan_estimates_user_id ON loan_estimates (user_id);
COMMENT ON TABLE loan_estimates IS '마이페이지 「대출 시뮬레이션 결과」 탭. 결정적 계산이며 LLM 미사용';

CREATE TABLE loan_actuals (
    id           BIGSERIAL     PRIMARY KEY,
    user_id      BIGINT        NOT NULL REFERENCES users (id),
    policy_id    BIGINT        NOT NULL REFERENCES policies (id),
    actual_limit BIGINT        NOT NULL,
    actual_rate  DECIMAL(4, 2),
    bank_name    VARCHAR(50),
    created_at   TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX idx_loan_actuals_policy_id ON loan_actuals (policy_id);
COMMENT ON TABLE loan_actuals IS '⚠️ 팀 논의 필요 — 커뮤니티 배너의 「실제」 막대 원천이나, 입력 화면이 와이어프레임에 없음';

-- ─────────────── 6. 실거래가 ───────────────

CREATE TABLE regions (
    region_code VARCHAR(10)  PRIMARY KEY,
    region_name VARCHAR(100) NOT NULL,
    sido        VARCHAR(30)  NOT NULL,
    sigungu     VARCHAR(30)
);
COMMENT ON TABLE regions IS '와이어프레임 11 좌측 「지역」 필터의 선택지';

CREATE TABLE real_estate_transactions (
    id             BIGSERIAL     PRIMARY KEY,
    region_code    VARCHAR(10)   NOT NULL REFERENCES regions (region_code),
    apt_name       VARCHAR(255)  NOT NULL,
    deal_amount    BIGINT        NOT NULL,
    exclusive_area DECIMAL(6, 2) NOT NULL,
    floor          INT,
    build_year     INT,
    deal_date      DATE          NOT NULL,
    deal_type      VARCHAR(20)   NOT NULL,
    latitude       DECIMAL(10, 7),
    longitude      DECIMAL(10, 7),
    crawl_job_id   BIGINT        REFERENCES crawl_jobs (id),
    crawled_at     TIMESTAMP     NOT NULL,
    CONSTRAINT uq_transaction UNIQUE (region_code, apt_name, deal_date, exclusive_area, floor)
);
CREATE INDEX idx_transactions_region_date ON real_estate_transactions (region_code, deal_date);
CREATE INDEX idx_transactions_apt_date    ON real_estate_transactions (apt_name, deal_date);
COMMENT ON TABLE real_estate_transactions IS '크롤러가 직접 INSERT. 와이어프레임 11 지도 마커의 원천';
