"""크롤러 진입점.

    python -m zipsa_crawler.main --target transaction                # 서울 25개구, 최근 3개월
    python -m zipsa_crawler.main --target transaction --region 11680 --months 1
    python -m zipsa_crawler.main --target policy                     # (미구현)

각 실행은 crawl_jobs 에 기록됩니다. 중간에 실패해도 어디까지 갔는지 남습니다.
"""

from __future__ import annotations

import argparse
import logging
import sys

from .config import load_settings
from .db import connect, fail_job, finish_job, start_job

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)-5s %(name)s | %(message)s",
)
log = logging.getLogger("zipsa.crawler")

TARGETS = {
    "policy": "POLICY",
    "public-housing": "PUBLIC_HOUSING",
    "transaction": "TRANSACTION",
    "news": "NEWS",
}


def run(target_key: str, region: str | None, months: int) -> int:
    settings = load_settings()
    target = TARGETS[target_key]

    with connect(settings) as conn:
        job_id = start_job(conn, target, region)
        log.info("크롤링 시작 — target=%s job_id=%s", target, job_id)

        try:
            if target_key == "transaction":
                from .transaction import collect
                processed = collect(conn, settings, job_id, months=months, only_region=region)
            elif target_key == "policy":
                from .policy import collect_policies
                processed = collect_policies(conn, settings, job_id)
            elif target_key == "public-housing":
                from .public_housing import collect_public_housing
                processed = collect_public_housing(conn, settings, job_id, only_region=region)
            elif target_key == "news":
                from .news import collect_news
                processed = collect_news(conn, settings, job_id)
            else:
                processed = 0
                log.warning("수집기가 아직 구현되지 않았습니다. (target=%s)", target)

            finish_job(conn, job_id, processed)
            log.info("크롤링 완료 — %s건 적재", processed)
            return 0

        except Exception as e:  # noqa: BLE001 — Job 상태를 남기고 실패를 그대로 올린다
            conn.rollback()
            fail_job(conn, job_id, repr(e))
            log.exception("크롤링 실패 — job_id=%s", job_id)
            return 1


def main() -> int:
    parser = argparse.ArgumentParser(description="ZipSa 크롤러")
    parser.add_argument("--target", required=True, choices=sorted(TARGETS), help="수집 대상")
    parser.add_argument("--region", default=None, help="법정동코드 5자리 (예: 11680). 생략하면 전체")
    parser.add_argument("--months", type=int, default=3, help="최근 N개월 (기본 3, transaction 전용)")
    args = parser.parse_args()
    return run(args.target, args.region, args.months)


if __name__ == "__main__":
    sys.exit(main())
