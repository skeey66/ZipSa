"""실거래가 수집 실행부.

한 번의 API 호출이 "1개 시군구 × 1개월" 이라, 서울 25개 구 × 3개월 × (매매+전월세)
= 150 회 호출입니다. 오래 걸리므로 진행 상황을 로그로 남깁니다.
"""

from __future__ import annotations

import logging
from datetime import date

import psycopg

from ..config import Settings, require
from . import client, repository
from .geocode import Geocoder

log = logging.getLogger("zipsa.crawler.transaction")


def recent_months(count: int, today: date | None = None) -> list[str]:
    """최근 N개월을 YYYYMM 으로. 이번 달은 거래 신고가 아직 덜 들어와서 포함만 하고 기대는 낮게."""
    today = today or date.today()
    out, y, m = [], today.year, today.month
    for _ in range(count):
        out.append(f"{y}{m:02d}")
        m -= 1
        if m == 0:
            y, m = y - 1, 12
    return out


def collect(conn: psycopg.Connection, settings: Settings, job_id: int,
            months: int = 3, only_region: str | None = None) -> int:
    api_key = require(settings.data_go_kr_key, "DATA_GO_KR_SERVICE_KEY", "실거래가")
    kakao_key = require(settings.kakao_rest_key, "KAKAO_REST_API_KEY", "주소→좌표 변환")

    regions = repository.load_regions(conn)
    if only_region:
        regions = [r for r in regions if r[0] == only_region]
        if not regions:
            raise RuntimeError(f"지역코드 {only_region} 가 regions 표에 없습니다.")

    ym_list = recent_months(months)
    geocoder = Geocoder(kakao_key)
    total = 0

    log.info("대상: %d개 지역 × %d개월 (%s ~ %s)",
             len(regions), len(ym_list), ym_list[-1], ym_list[0])

    for idx, (region_code, sido, sigungu) in enumerate(regions, start=1):
        geocoder.preload(repository.known_coordinates(conn, region_code))
        region_total = 0

        for ym in ym_list:
            deals: list[client.Deal] = []
            for kind in ("SALE", "RENT"):
                deals.extend(client.fetch(api_key, kind, region_code, ym,
                                          delay=settings.delay_seconds))

            located = []
            for d in deals:
                # 같은 아파트는 한 번만 물어봅니다.
                cache_key = f"{d.region_code}|{d.apt_name}"
                address = f"{sido} {sigungu} {d.umd_nm} {d.jibun}".strip()
                located.append((d, geocoder.lookup(cache_key, address, d.apt_name)))

            region_total += repository.upsert_many(conn, located, job_id)
            conn.commit()   # 지역·월 단위로 커밋해서 중간에 끊겨도 앞부분은 남깁니다

        total += region_total
        log.info("[%2d/%2d] %s %s — %d건 (누적 %d)",
                 idx, len(regions), sido, sigungu, region_total, total)

    log.info(geocoder.summary())
    return total
