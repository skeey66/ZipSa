"""공공임대 적재."""

from __future__ import annotations

import psycopg

from .client import Complex, Notice


def upsert_notices(conn: psycopg.Connection, notices: list[Notice], job_id: int) -> int:
    if not notices:
        return 0
    with conn.cursor() as cur:
        cur.executemany(
            """INSERT INTO public_housings
                 (external_id, name, housing_type, region, recruit_start_date, recruit_end_date,
                  apply_url, source_url, crawled_at, crawl_job_id)
               VALUES (%s,%s,%s,%s,%s,%s,%s,%s, now(), %s)
               ON CONFLICT (external_id) DO UPDATE SET
                 name               = EXCLUDED.name,
                 housing_type       = EXCLUDED.housing_type,
                 region             = EXCLUDED.region,
                 recruit_start_date = EXCLUDED.recruit_start_date,
                 recruit_end_date   = EXCLUDED.recruit_end_date,
                 apply_url          = EXCLUDED.apply_url,
                 crawl_job_id       = EXCLUDED.crawl_job_id,
                 crawled_at         = now(),
                 updated_at         = now()""",
            [(n.external_id, n.name, n.housing_type, n.region, n.recruit_start_date,
              n.recruit_end_date, n.apply_url, n.source_url, job_id) for n in notices],
        )
    return len(notices)


def known_coordinates(conn: psycopg.Connection) -> dict[str, tuple[float, float]]:
    """이미 좌표가 있는 주소를 캐시로 돌려줍니다(카카오 호출 절약).

    V21 부터 단지가 좌표를 하나만 갖습니다. 예전에는 평형 행 48,886개에서
    DISTINCT ON 으로 주소를 추려내야 했습니다.
    """
    with conn.cursor() as cur:
        cur.execute(
            """SELECT road_address, latitude, longitude
               FROM housing_complexes
               WHERE road_address IS NOT NULL AND latitude IS NOT NULL"""
        )
        return {addr: (float(la), float(lo)) for addr, la, lo in cur.fetchall()}


def upsert_complexes(conn: psycopg.Connection,
                     rows: list[tuple[Complex, tuple[float, float] | None]],
                     job_id: int) -> int:
    """단지를 먼저 만들고, 평형을 그 단지에 붙입니다(V21 정규화).

    마이홈 API 는 「단지 × 평형」 한 줄로 내려주므로 여기서 두 층으로 나눕니다.
    """
    if not rows:
        return 0

    with conn.cursor() as cur:
        # ── 1. 단지 ──
        # 한 단지에 평형이 여럿이라 먼저 중복을 걷어냅니다. 그러지 않으면 같은
        # INSERT 문 안에서 같은 complex_no 가 두 번 나와 ON CONFLICT 가 걸립니다.
        seen: dict[int, tuple] = {}
        for c, coord in rows:
            prev = seen.get(c.complex_no)
            if prev is None or (prev[-2] is None and coord is not None):
                seen[c.complex_no] = (
                    c.complex_no, c.name, c.institution,
                    # 마이홈은 시군구를 뒤 3자리로 주므로 붙여야 법정동코드 5자리가 됩니다.
                    f"{c.sido_code}{c.sigungu_code}",
                    c.road_address, c.house_type, c.household_count,
                    c.parking_count, c.completed_date,
                    coord[0] if coord else None, coord[1] if coord else None,
                    job_id,
                )

        cur.executemany(
            """INSERT INTO housing_complexes
                 (complex_no, name, institution, region_code, road_address, house_type,
                  household_count, parking_count, completed_date, latitude, longitude,
                  crawl_job_id, crawled_at)
               VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s, now())
               ON CONFLICT (complex_no) DO UPDATE SET
                 name            = EXCLUDED.name,
                 road_address    = EXCLUDED.road_address,
                 household_count = COALESCE(EXCLUDED.household_count, housing_complexes.household_count),
                 -- 좌표는 새로 못 찾았을 때 기존 값을 지우지 않습니다
                 latitude        = COALESCE(EXCLUDED.latitude,  housing_complexes.latitude),
                 longitude       = COALESCE(EXCLUDED.longitude, housing_complexes.longitude),
                 crawl_job_id    = EXCLUDED.crawl_job_id,
                 crawled_at      = now()""",
            list(seen.values()),
        )

        # ── 2. 평형 ──
        cur.execute(
            "SELECT complex_no, id FROM housing_complexes WHERE complex_no = ANY(%s)",
            (list(seen.keys()),),
        )
        complex_id = dict(cur.fetchall())

        cur.executemany(
            """INSERT INTO housing_complex_units
                 (complex_id, external_id, housing_type, style_name,
                  exclusive_area, supply_area, deposit, monthly_rent)
               VALUES (%s,%s,%s,%s,%s,%s,%s,%s)
               ON CONFLICT (external_id) DO UPDATE SET
                 housing_type   = EXCLUDED.housing_type,
                 exclusive_area = EXCLUDED.exclusive_area,
                 supply_area    = EXCLUDED.supply_area,
                 deposit        = EXCLUDED.deposit,
                 monthly_rent   = EXCLUDED.monthly_rent""",
            [(complex_id[c.complex_no], c.external_id, c.housing_type, c.style_name,
              c.exclusive_area, c.supply_area, c.deposit, c.monthly_rent)
             for c, _ in rows],
        )
    return len(rows)
