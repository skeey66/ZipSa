-- ============================================================
--  뉴스 (명세 v4 §5, 상단바 「정보」 하위)
--
--  ⚠️ 기사 본문(content)은 저장하지 않습니다.
--     기사 본문은 언론사의 저작물이고, DB 에 담아 우리 화면에 뿌리면
--     복제·전송에 해당합니다. 제목·요약·원문 링크만 두고 본문은
--     원문 사이트로 보냅니다. 명세 5.2 의 content 필드를 뺀 이유입니다.
--
--  수집은 크롤링이 아니라 언론사가 공개한 RSS 를 읽습니다.
-- ============================================================

CREATE TABLE news (
    id            BIGSERIAL    PRIMARY KEY,
    external_id   VARCHAR(500) NOT NULL UNIQUE,
    title         VARCHAR(500) NOT NULL,
    summary       TEXT,
    press_name    VARCHAR(100),
    source_url    VARCHAR(1000) NOT NULL,
    thumbnail_url VARCHAR(1000),
    published_at  TIMESTAMP    NOT NULL,
    crawl_job_id  BIGINT       REFERENCES crawl_jobs (id),
    crawled_at    TIMESTAMP    NOT NULL DEFAULT now(),
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_news_published ON news (published_at DESC);

COMMENT ON COLUMN news.external_id  IS 'RSS 의 guid 또는 원문 URL. 크롤러가 이 값으로 upsert';
COMMENT ON COLUMN news.summary      IS 'RSS description 을 2~3줄로 자른 것. 기사 본문이 아님';
COMMENT ON COLUMN news.source_url   IS '원문 링크. 상세 화면은 이 링크로 보낸다';
