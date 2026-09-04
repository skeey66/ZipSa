"""DB 커넥션과 크롤링 Job 기록.

적재된 모든 행은 crawl_job_id 를 갖습니다. 잘못 적재했을 때
`DELETE ... WHERE crawl_job_id = ?` 로 한 번에 되돌리기 위해서입니다.
"""

from __future__ import annotations

from contextlib import contextmanager
from typing import Iterator, Literal

import psycopg

from .config import Settings

CrawlTarget = Literal["POLICY", "PUBLIC_HOUSING", "TRANSACTION"]


@contextmanager
def connect(settings: Settings) -> Iterator[psycopg.Connection]:
    with psycopg.connect(settings.dsn) as conn:
        yield conn


def start_job(conn: psycopg.Connection, target: CrawlTarget, region: str | None = None) -> int:
    with conn.cursor() as cur:
        cur.execute(
            """
            INSERT INTO crawl_jobs (target, target_region, status, started_at)
            VALUES (%s, %s, 'RUNNING', now())
            RETURNING id
            """,
            (target, region),
        )
        job_id = cur.fetchone()[0]
    conn.commit()
    return job_id


def finish_job(conn: psycopg.Connection, job_id: int, processed: int) -> None:
    with conn.cursor() as cur:
        cur.execute(
            """
            UPDATE crawl_jobs
               SET status = 'SUCCESS', processed_count = %s, finished_at = now()
             WHERE id = %s
            """,
            (processed, job_id),
        )
    conn.commit()


def fail_job(conn: psycopg.Connection, job_id: int, error: str) -> None:
    with conn.cursor() as cur:
        cur.execute(
            """
            UPDATE crawl_jobs
               SET status = 'FAILED', error_message = %s, finished_at = now()
             WHERE id = %s
            """,
            (error[:2000], job_id),
        )
    conn.commit()
