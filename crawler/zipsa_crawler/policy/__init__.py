"""청년정책 수집기."""

from __future__ import annotations

import logging

import psycopg

from ..config import Settings
from .client import fetch
from .repository import upsert_many

log = logging.getLogger("zipsa.crawler.policy")

__all__ = ["collect_policies"]


def collect_policies(conn: psycopg.Connection, settings: Settings, job_id: int) -> int:
    log.info("청년정책 수집 시작 (온통청년 공식 OpenAPI)")
    policies = fetch(settings.youth_center_key, delay=settings.delay_seconds)
    count = upsert_many(conn, policies, job_id)
    conn.commit()

    from collections import Counter
    dist = Counter(p.category for p in policies)
    log.info("정책 %d건 적재 — %s", count,
             " / ".join(f"{k} {v}" for k, v in dist.most_common()))
    return count
