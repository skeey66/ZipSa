"""뉴스 수집기."""

from __future__ import annotations

import logging

import psycopg

from ..config import Settings
from .article import fill_contents
from .client import FEEDS, fetch_feed
from .repository import upsert_many

log = logging.getLogger("zipsa.crawler.news")

__all__ = ["collect_news"]


def collect_news(conn: psycopg.Connection, settings: Settings, job_id: int,
                 with_content: bool = True) -> int:
    """언론사 RSS 를 돌며 청년 주거 관련 기사만 적재합니다. API 키가 필요 없습니다."""
    log.info("RSS %d개 수집 시작", len(FEEDS))

    seen: set[str] = set()
    articles = []
    for press, url in FEEDS:
        for article in fetch_feed(press, url, settings.user_agent):
            # 같은 기사가 여러 피드에 뜨는 경우가 있어 배치 안에서 먼저 걸러냅니다.
            # (DB 의 ON CONFLICT 로도 막히지만, executemany 안에서 같은 키가 두 번
            #  나오면 "ON CONFLICT DO UPDATE 가 같은 행을 두 번 건드린다" 오류가 납니다)
            if article.external_id in seen:
                continue
            seen.add(article.external_id)
            articles.append(article)

    if with_content:
        log.info("본문 추출 시작 (%d건)", len(articles))
        filled = fill_contents(articles, settings.user_agent)
        log.info("본문 %d/%d 건 확보", filled, len(articles))

    count = upsert_many(conn, articles, job_id)
    conn.commit()
    log.info("뉴스 %d건 적재 (언론사 %d곳)", count, len({a.press_name for a in articles}))
    return count
