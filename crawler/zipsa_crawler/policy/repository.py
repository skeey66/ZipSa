"""청년정책 적재."""

from __future__ import annotations

import psycopg

from .client import Policy


def upsert_many(conn: psycopg.Connection, policies: list[Policy], job_id: int) -> int:
    if not policies:
        return 0
    with conn.cursor() as cur:
        cur.executemany(
            """INSERT INTO policies
                 (external_id, title, content, category, region, issuer,
                  target_job, target_age_range, target_salary_range,
                  target_min_age, target_max_age, earn_min_amt, earn_max_amt,
                  marital_condition, keyword,
                  apply_start_date, apply_end_date, apply_method,
                  zip_codes, sido_codes,
                  source_name, source_url, crawled_at, crawl_job_id)
               VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,
                       '온통청년', %s, now(), %s)
               ON CONFLICT (external_id) DO UPDATE SET
                 title               = EXCLUDED.title,
                 content             = EXCLUDED.content,
                 category            = EXCLUDED.category,
                 region              = EXCLUDED.region,
                 issuer              = EXCLUDED.issuer,
                 target_job          = EXCLUDED.target_job,
                 target_age_range    = EXCLUDED.target_age_range,
                 target_salary_range = EXCLUDED.target_salary_range,
                 target_min_age      = EXCLUDED.target_min_age,
                 target_max_age      = EXCLUDED.target_max_age,
                 earn_min_amt        = EXCLUDED.earn_min_amt,
                 earn_max_amt        = EXCLUDED.earn_max_amt,
                 marital_condition   = EXCLUDED.marital_condition,
                 keyword             = EXCLUDED.keyword,
                 apply_start_date    = EXCLUDED.apply_start_date,
                 apply_end_date      = EXCLUDED.apply_end_date,
                 apply_method        = EXCLUDED.apply_method,
                 zip_codes           = EXCLUDED.zip_codes,
                 sido_codes          = EXCLUDED.sido_codes,
                 crawl_job_id        = EXCLUDED.crawl_job_id,
                 crawled_at          = now(),
                 updated_at          = now()""",
            [(p.external_id, p.title, p.content, p.category, p.region, p.issuer,
              p.target_job, p.target_age_range, p.target_salary_range,
              p.target_min_age, p.target_max_age, p.earn_min_amt, p.earn_max_amt,
              p.marital_condition, p.keyword,
              p.apply_start_date, p.apply_end_date, p.apply_method,
              p.zip_codes, p.sido_codes,
              p.source_url, job_id) for p in policies],
        )
    return len(policies)
