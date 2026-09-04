"""전국 시군구 법정동코드를 국토부 API 로 검증합니다.

    crawler/.venv/bin/python scripts/discover_regions.py > /tmp/regions.sql

무효한 코드도 200 OK 에 totalCount=0 을 주기 때문에 "코드가 맞는지" 는 알 수 없습니다.
대신 "거래가 실제로 잡히는 코드" 만 남기면 우리 목적에는 충분합니다.
지역명은 응답의 estateAgentSggNm 최빈값에서 가져옵니다(오타 낼 일이 없습니다).
"""

from __future__ import annotations

import os
import sys
import time
import xml.etree.ElementTree as ET
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import requests
from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parents[1] / ".env")
KEY = os.getenv("DATA_GO_KR_SERVICE_KEY", "")
SALE = "https://apis.data.go.kr/1613000/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev"
RENT = "https://apis.data.go.kr/1613000/RTMSDataSvcAptRent/getRTMSDataSvcAptRent"
MONTHS = ["202607", "202606", "202605"]

# 후보. 여기 없는 코드는 조회되지 않으므로 넉넉히 넣고 검증으로 걸러냅니다.
CANDIDATES: dict[str, list[str]] = {
    "11": "110 140 170 200 215 230 260 290 305 320 350 380 410 440 470 500 530 545 560 590 620 650 680 710 740".split(),
    "26": "110 140 170 200 230 260 290 320 350 380 410 440 470 500 530 710".split(),
    "27": "110 140 170 200 230 260 290 710 720".split(),
    # 28110 중구·28140 동구는 2026년 제물포구(28125)·영종구(28155)로 개편되어 거래가 잡히지 않습니다.
    "28": "110 125 140 155 177 185 200 237 245 260 710 720".split(),
    "29": "110 140 155 170 200".split(),
    "30": "110 140 170 200 230".split(),
    "31": "110 140 170 200 710".split(),
    "36": "110".split(),
    "41": ("111 113 115 117 131 133 135 150 171 173 190 210 220 250 271 273 281 285 287 290 "
           "310 360 370 390 410 430 450 461 463 465 480 500 550 570 590 610 630 650 670 800 820 830").split(),
    "43": "111 112 113 114 130 150 720 730 740 745 750 760 770 800".split(),
    "44": "130 131 150 180 200 210 230 250 270 710 760 770 790 800 810 825".split(),
    "45": "111 113 130 140 180 190 210 710 720 730 740 750 770 790 800".split(),
    "46": "110 130 150 170 230 710 720 730 770 780 790 800 810 820 830 840 860 870 880 890 900 910".split(),
    "47": "111 113 130 150 170 190 210 230 250 280 290 730 750 760 770 820 830 840 920".split(),
    "48": "121 123 125 127 129 170 220 240 250 270 310 330 720 730 740 820 840 850 860 870 880 890".split(),
    "50": "110 130".split(),
    "51": "110 130 150 170 190 210 230 720 730 750 760 770 780 790 800 810 820 830".split(),
    "52": "111 113 130 140 180 190 210 710 720 730 740 750 770 790 800".split(),
}


def probe(code: str) -> tuple[str, int, str] | None:
    """(코드, 거래건수, 지역명). 거래가 잡히지 않으면 None.

    ⚠️ 초당 요청 제한(429)을 0건으로 착각하면 안 됩니다. 그렇게 하면
       "광주광역시 전체에 아파트 거래가 없다" 같은 결론이 나옵니다(실제로 겪었습니다).
       크롤러 본체(transaction/client.py)와 같은 재시도 규칙을 씁니다.
    """
    for ym in MONTHS:
        body = None
        for attempt in range(5):
            try:
                r = requests.get(SALE, params={"serviceKey": KEY, "LAWD_CD": code,
                                               "DEAL_YMD": ym, "numOfRows": 100, "pageNo": 1},
                                 timeout=30)
            except requests.RequestException:
                time.sleep(2 ** attempt)
                continue
            if "REQUESTS_PER_SECOND_EXCEEDS" in r.text or "REQUESTS_EXCEEDS" in r.text:
                time.sleep(2 ** attempt)
                continue
            body = r.text
            break
        if body is None:
            raise RuntimeError(f"{code}: 요청 제한으로 확인 실패")

        root = ET.fromstring(body)
        total_el = root.find(".//totalCount")
        if total_el is None:
            raise RuntimeError(f"{code}: totalCount 없는 응답 — {' '.join(body.split())[:120]}")

        count = int(total_el.text) if total_el.text and total_el.text.isdigit() else 0
        if not count:
            continue  # 이 달에 거래가 없을 뿐일 수 있으니 다음 달로

        names = Counter(e.text.strip() for e in root.findall(".//estateAgentSggNm")
                        if e.text and e.text.strip())
        return code, count, (names.most_common(1)[0][0] if names else "")

    return None


def main() -> int:
    if not KEY:
        print("DATA_GO_KR_SERVICE_KEY 가 없습니다.", file=sys.stderr)
        return 1

    codes = [sido + suf for sido, sufs in CANDIDATES.items() for suf in sufs]
    print(f"후보 {len(codes)}개 검증 중...", file=sys.stderr)

    found: list[tuple[str, int, str]] = []
    with ThreadPoolExecutor(max_workers=3) as pool:
        for res in pool.map(probe, codes):
            if res:
                found.append(res)
                print(f"  {res[0]}  {res[2] or '(이름 없음)':16} {res[1]:>5}건", file=sys.stderr)

    missing = sorted(set(codes) - {c for c, _, _ in found})
    print(f"\n확인됨 {len(found)}개 / 거래 없음 {len(missing)}개", file=sys.stderr)
    if missing:
        print(f"  제외: {' '.join(missing)}", file=sys.stderr)

    # SQL 출력
    rows = []
    for code, _, name in sorted(found):
        parts = name.split(maxsplit=1)
        sido = parts[0] if parts else ""
        sigungu = parts[1] if len(parts) > 1 else ""
        full = f"{sido} {sigungu}".strip()
        rows.append(f"    ('{code}', '{full}', '{sido}', '{sigungu}')")
    print("INSERT INTO regions (region_code, region_name, sido, sigungu) VALUES")
    print(",\n".join(rows))
    print("ON CONFLICT (region_code) DO NOTHING;")
    return 0


if __name__ == "__main__":
    sys.exit(main())
