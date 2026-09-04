"""공공임대 수집기 — 모집공고(LH) + 단지정보(마이홈포털)."""

from __future__ import annotations

import logging
from datetime import date, timedelta

import psycopg

from ..config import Settings, require
from ..transaction.geocode import Geocoder
from . import repository
from .client import fetch_complexes, fetch_notices

log = logging.getLogger("zipsa.crawler.housing")

__all__ = ["collect_public_housing"]


def collect_public_housing(conn: psycopg.Connection, settings: Settings, job_id: int,
                           only_region: str | None = None, months: int = 12) -> int:
    api_key = require(settings.data_go_kr_key, "DATA_GO_KR_SERVICE_KEY", "공공임대")
    kakao_key = require(settings.kakao_rest_key, "KAKAO_REST_API_KEY", "주소→좌표 변환")

    total = 0

    # ── ① 모집 공고 ─────────────────────────────
    today = date.today()
    notices = fetch_notices(api_key, today - timedelta(days=months * 30), today + timedelta(days=365))
    total += repository.upsert_notices(conn, notices, job_id)
    conn.commit()
    log.info("모집공고 %d건 적재", len(notices))

    # ── ② 단지 정보 ─────────────────────────────
    with conn.cursor() as cur:
        if only_region:
            cur.execute("SELECT region_code, region_name FROM regions WHERE region_code = %s",
                        (only_region,))
        else:
            # 단지 수가 많아 기본은 서울부터. 넓히려면 --region 으로 지정합니다.
            cur.execute("SELECT region_code, region_name FROM regions "
                        "WHERE region_code LIKE '11%' ORDER BY region_code")
        regions = cur.fetchall()

    if not regions:
        raise RuntimeError(f"지역코드 {only_region} 가 regions 표에 없습니다.")

    geocoder = Geocoder(kakao_key)
    geocoder.preload(repository.known_coordinates(conn))

    for idx, (code, name) in enumerate(regions, start=1):
        # ⚠️ 마이홈 API 는 시군구를 뒤 3자리로 받습니다(강남구 11680 → 680).
        rows = fetch_complexes(api_key, code[:2], code[2:])
        located = []
        for c in rows:
            coord = geocoder.lookup(c.road_address, c.road_address, c.name) if c.road_address else None
            located.append((c, coord))

        n = repository.upsert_complexes(conn, located, job_id)
        conn.commit()
        total += n
        log.info("[%2d/%2d] %s — 단지 %d건 (누적 %d)", idx, len(regions), name, n, total)

    log.info(geocoder.summary())
    return total
