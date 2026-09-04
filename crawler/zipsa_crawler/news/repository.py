"""뉴스 적재."""

from __future__ import annotations

import psycopg

from .client import Article


def upsert_many(conn: psycopg.Connection, articles: list[Article], job_id: int) -> int:
    """external_id 기준 upsert. 같은 기사가 여러 번 수집돼도 한 행만 남습니다."""
    if not articles:
        return 0
    with conn.cursor() as cur:
        cur.executemany(
            """INSERT INTO news
                 (external_id, title, summary, content, press_name, source_url,
                  published_at, crawl_job_id, crawled_at)
               VALUES (%s,%s,%s,%s,%s,%s,%s,%s, now())
               ON CONFLICT (external_id) DO UPDATE SET
                 title        = EXCLUDED.title,
                 summary      = EXCLUDED.summary,
                 -- 본문 추출에 실패했을 때 이미 있던 본문을 지우지 않습니다
                 content      = COALESCE(EXCLUDED.content, news.content),
                 published_at = EXCLUDED.published_at,
                 crawl_job_id = EXCLUDED.crawl_job_id,
                 crawled_at   = now()""",
            [(a.external_id, a.title, a.summary, a.content, a.press_name, a.source_url,
              a.published_at, job_id) for a in articles],
        )
    return len(articles)
