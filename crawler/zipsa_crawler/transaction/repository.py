"""실거래가 적재.

V20 부터 단지(apartments)와 거래(real_estate_transactions)가 분리됐습니다.
좌표·건축년도는 단지에 한 번만 쓰고, 거래는 단지를 가리키기만 합니다.
"""

from __future__ import annotations

import psycopg

from .client import Deal


def load_regions(conn: psycopg.Connection) -> list[tuple[str, str, str]]:
    """(region_code, sido, sigungu) — 크롤링 대상 지역."""
    with conn.cursor() as cur:
        cur.execute("SELECT region_code, sido, sigungu FROM regions ORDER BY region_code")
        return cur.fetchall()


def known_coordinates(conn: psycopg.Connection, region_code: str) -> dict[str, tuple[float, float]]:
    """이미 좌표가 채워진 단지를 캐시 형태로 돌려줍니다(카카오 호출 절약).

    예전에는 거래 41,600행에서 DISTINCT ON 으로 단지를 추려냈습니다.
    이제 단지 테이블을 그대로 읽으면 됩니다.
    """
    with conn.cursor() as cur:
        cur.execute(
            """SELECT region_code || '|' || name, latitude, longitude
               FROM apartments
               WHERE region_code = %s AND latitude IS NOT NULL""",
            (region_code,),
        )
        return {k: (float(la), float(lo)) for k, la, lo in cur.fetchall()}


def upsert_many(conn: psycopg.Connection, deals: list[tuple[Deal, tuple[float, float] | None]],
                job_id: int) -> int:
    """단지를 먼저 만들고, 거래를 그 단지에 붙입니다.

    같은 거래가 재수집돼도 중복되지 않게 unique 제약으로 덮어씁니다.
    """
    if not deals:
        return 0

    with conn.cursor() as cur:
        # ── 1. 단지 ──
        # 한 단지에 거래가 여럿이므로 먼저 중복을 걷어냅니다. 그러지 않으면 같은
        # INSERT 문 안에서 같은 키가 두 번 나와 ON CONFLICT 가 걸립니다.
        seen: dict[tuple[str, str], tuple] = {}
        for d, coord in deals:
            key = (d.region_code, d.apt_name)
            # 좌표를 찾은 행이 하나라도 있으면 그 값을 씁니다.
            prev = seen.get(key)
            if prev is None or (prev[3] is None and coord is not None):
                seen[key] = (d.region_code, d.apt_name, d.build_year,
                             coord[0] if coord else None, coord[1] if coord else None)

        cur.executemany(
            """INSERT INTO apartments (region_code, name, build_year, latitude, longitude)
               VALUES (%s,%s,%s,%s,%s)
               ON CONFLICT (region_code, name) DO UPDATE SET
                 build_year = COALESCE(EXCLUDED.build_year, apartments.build_year),
                 -- 좌표는 새로 못 찾았을 때 기존 값을 지우지 않습니다
                 latitude   = COALESCE(EXCLUDED.latitude,  apartments.latitude),
                 longitude  = COALESCE(EXCLUDED.longitude, apartments.longitude)""",
            [(rc, nm, by, la, lo) for rc, nm, by, la, lo in seen.values()],
        )

        # ── 2. 거래 ──
        # apartment_id 를 미리 조회해 두면 거래 INSERT 마다 서브쿼리를 돌지 않아도 됩니다.
        cur.execute(
            "SELECT region_code, name, id FROM apartments WHERE region_code = ANY(%s)",
            ([rc for rc, _ in seen], ),
        )
        apt_id = {(rc, nm): i for rc, nm, i in cur.fetchall()}

        cur.executemany(
            """INSERT INTO real_estate_transactions
                 (apartment_id, deal_amount, monthly_rent, exclusive_area, floor,
                  deal_date, deal_type, crawl_job_id, crawled_at)
               VALUES (%s,%s,%s,%s,%s,%s,%s,%s, now())
               ON CONFLICT (apartment_id, deal_date, exclusive_area, floor, deal_type)
               DO UPDATE SET
                 deal_amount  = EXCLUDED.deal_amount,
                 monthly_rent = EXCLUDED.monthly_rent,
                 crawl_job_id = EXCLUDED.crawl_job_id,
                 crawled_at   = now()""",
            [
                (apt_id[(d.region_code, d.apt_name)], d.deal_amount, d.monthly_rent,
                 d.exclusive_area, d.floor, d.deal_date, d.deal_type, job_id)
                for d, _ in deals
            ],
        )
    return len(deals)
