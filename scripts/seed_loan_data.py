"""화면 14 「나의 대출 예측하기」의 그래프 원천 데이터.

    crawler/.venv/bin/python scripts/seed_loan_data.py [--reset]

⚠️ 목업입니다. 실제 은행 심사 결과가 아닙니다.
   화면 14 의 은행별 막대그래프는 "우리 회원들이 실제로 얼마를 승인받았나" 를
   구간별로 센 것입니다. 그러려면 표본이 있어야 하는데 실제 데이터가 없으므로
   시드 회원 160명의 프로필에 맞춰 그럴듯한 승인 이력을 만듭니다.

■ 무작위로 뿌리지 않는 이유
   균등 난수면 은행별·소득별 차이가 사라져서 그래프 4개가 똑같이 생깁니다.
   그러면 "내 조건이면 어느 은행이 유리한가" 라는 화면의 목적이 무너집니다.
   그래서 ① 소득이 높을수록 한도가 크고 ② 은행마다 성향이 다르게 만듭니다.

■ 왜 일부 회원만 기록을 갖는가
   대출 기록이 있는 회원에게는 커뮤니티에 은행 뱃지가 붙습니다.
   전원이 갖고 있으면 "실제로 받아본 사람" 이라는 신호가 사라집니다.
   그래서 약 35%만 신청 이력을 갖되, 그 사람들은 여러 은행에 신청한 것으로 둡니다
   (실제로도 대출은 두세 곳 이상 알아봅니다). 그래야 그래프 표본은 유지됩니다.

■ 반려도 함께 만듭니다
   승인만 모으면 "떨어질 수도 있다" 를 알 수 없습니다.
   소득 증빙이 어려운 조건일수록 반려율을 높게 잡습니다.
"""

from __future__ import annotations

import argparse
import os
import random
import sys
from pathlib import Path

import psycopg
from dotenv import load_dotenv

ROOT = Path(__file__).resolve().parents[1]
load_dotenv(ROOT / ".env")

SEED = 20260903
LOGIN_PREFIX = "seed_user_"

# 은행별 성향. base 는 한도 배수, spread 는 편차, rate 는 기준 금리.
#   · 국민/우리 : 무난
#   · 농협      : 한도는 낮지만 금리가 싸다
#   · 하나      : 고소득에 후하다
BANKS = [
    {"name": "KB국민은행", "base": 1.00, "spread": 0.18, "rate": 3.6},
    {"name": "우리은행",   "base": 1.05, "spread": 0.20, "rate": 3.7},
    {"name": "NH농협은행", "base": 0.92, "spread": 0.15, "rate": 3.3},
    {"name": "하나은행",   "base": 1.12, "spread": 0.22, "rate": 3.9},
]

# 연소득 구간 → 기준 한도(원). 전세자금대출 실무 감각에 맞춘 값입니다.
SALARY_BASE = {
    "UNDER_2000":      70_000_000,
    "RANGE_2000_3000": 100_000_000,
    "RANGE_3000_4000": 140_000_000,
    "RANGE_4000_5000": 175_000_000,
    "RANGE_5000_7000": 210_000_000,
    "OVER_7000":       250_000_000,
}
# 직업별 가중. 소득 증빙이 쉬울수록 한도가 큽니다.
JOB_FACTOR = {
    "EMPLOYEE": 1.00, "SELF_EMPLOYED": 0.82, "STUDENT": 0.55,
    "JOB_SEEKER": 0.50, "ETC": 0.75,
}


def connect() -> psycopg.Connection:
    return psycopg.connect(
        host=os.getenv("POSTGRES_HOST", "localhost"),
        port=int(os.getenv("POSTGRES_PORT", "5432")),
        dbname=os.getenv("POSTGRES_DB", "zipsa"),
        user=os.getenv("POSTGRES_USER", "zipsa"),
        password=os.getenv("POSTGRES_PASSWORD", "changeme"),
    )


# 대출 신청 이력을 가질 회원 비율. 나머지는 뱃지가 없습니다.
APPLICANT_RATIO = 0.35

# 직업·소득별 반려율. 균등하게 주면 그래프의 반려 막대가 조건과 무관해집니다.
REJECT_RATE = {
    "JOB_SEEKER": 0.34, "STUDENT": 0.34,
    "SELF_EMPLOYED": 0.22, "ETC": 0.14, "EMPLOYEE": 0.10,
}
LOW_INCOME = ("UNDER_2000", "RANGE_2000_3000")


def clean(conn: psycopg.Connection) -> None:
    with conn.cursor() as cur:
        cur.execute(
            f"""DELETE FROM loan_actuals
                WHERE user_id IN (SELECT id FROM users WHERE login_id LIKE '{LOGIN_PREFIX}%')"""
        )
        deleted = cur.rowcount
    conn.commit()
    print(f"  기존 대출 목업 {deleted}건 삭제")


def seed(conn: psycopg.Connection) -> None:
    rnd = random.Random(SEED)

    with conn.cursor() as cur:
        cur.execute(
            f"""SELECT id, salary_range, job FROM users
                WHERE login_id LIKE '{LOGIN_PREFIX}%' AND salary_range IS NOT NULL"""
        )
        users = cur.fetchall()
        if not users:
            print("시드 회원이 없습니다. 먼저 scripts/seed_dev_data.py 를 실행하세요.", file=sys.stderr)
            sys.exit(1)

        # 대출 실적은 정책과 묶입니다(loan_actuals.policy_id NOT NULL).
        # 실제 크롤링된 대출·이자 정책을 씁니다.
        cur.execute("SELECT id FROM policies WHERE category = 'LOAN' ORDER BY id")
        loan_policies = [r[0] for r in cur.fetchall()]
        if not loan_policies:
            print("LOAN 카테고리 정책이 없습니다. 정책 크롤러를 먼저 돌리세요.", file=sys.stderr)
            sys.exit(1)

        # 신청 이력을 가질 회원을 먼저 고릅니다(뱃지 보유자).
        applicants = rnd.sample(users, k=max(1, round(len(users) * APPLICANT_RATIO)))

        rows = []
        for uid, salary, job in applicants:
            base = SALARY_BASE.get(salary, 100_000_000) * JOB_FACTOR.get(job, 0.8)
            reject_rate = REJECT_RATE.get(job, 0.15) + (0.08 if salary in LOW_INCOME else 0)

            # 한 사람이 은행 2~4곳에 신청하고, 일부는 같은 은행에 재신청합니다.
            banks = rnd.sample(BANKS, k=rnd.randint(2, 4))
            for bank in banks:
                for _ in range(rnd.randint(1, 3)):
                    if rnd.random() < reject_rate:
                        rows.append((uid, rnd.choice(loan_policies), None, None,
                                     bank["name"], "REJECTED"))
                        continue
                    amount = base * bank["base"] * rnd.gauss(1.0, bank["spread"])
                    amount = max(30_000_000, min(400_000_000, amount))
                    rows.append((
                        uid,
                        rnd.choice(loan_policies),
                        int(round(amount / 1_000_000) * 1_000_000),   # 백만원 단위로 반올림
                        round(bank["rate"] + rnd.gauss(0, 0.35), 2),
                        bank["name"],
                        "APPROVED",
                    ))

        cur.executemany(
            """INSERT INTO loan_actuals
                 (user_id, policy_id, actual_limit, actual_rate, bank_name, status)
               VALUES (%s,%s,%s,%s,%s,%s)""",
            rows,
        )
    conn.commit()

    from collections import Counter
    dist = Counter(r[4] for r in rows)
    rejected = sum(1 for r in rows if r[5] == "REJECTED")
    holders = len({r[0] for r in rows})
    print(f"  회원 {len(users)}명 중 {holders}명({holders * 100 // len(users)}%)이 신청 이력 보유")
    print(f"  대출 기록 {len(rows)}건 (승인 {len(rows) - rejected} / 반려 {rejected})")
    for bank, n in dist.most_common():
        print(f"    {bank:12} {n:>4}건")


def main() -> int:
    ap = argparse.ArgumentParser(description="대출 예측 그래프용 목업 데이터")
    ap.add_argument("--reset", action="store_true", help="기존 목업 삭제 후 재생성")
    args = ap.parse_args()

    with connect() as conn:
        if args.reset:
            clean(conn)
        with conn.cursor() as cur:
            cur.execute(
                f"""SELECT count(*) FROM loan_actuals
                    WHERE user_id IN (SELECT id FROM users WHERE login_id LIKE '{LOGIN_PREFIX}%')"""
            )
            if cur.fetchone()[0]:
                print("이미 대출 목업이 있습니다. 다시 만들려면 --reset 을 주세요.", file=sys.stderr)
                return 1
        print("대출 목업 생성 중...")
        seed(conn)
    return 0


if __name__ == "__main__":
    sys.exit(main())
