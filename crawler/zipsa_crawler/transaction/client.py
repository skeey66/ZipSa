"""국토교통부 아파트 실거래가 API 클라이언트.

매매와 전월세는 엔드포인트가 다르지만 인증키(serviceKey)와 조회 방식은 같습니다.
한 번의 호출로 "1개 시군구 × 1개월" 만 조회됩니다.
"""

from __future__ import annotations

import logging
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import date

import requests

log = logging.getLogger("zipsa.crawler.transaction")

BASE = "https://apis.data.go.kr/1613000"
ENDPOINTS = {
    "SALE": f"{BASE}/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev",
    "RENT": f"{BASE}/RTMSDataSvcAptRent/getRTMSDataSvcAptRent",
}
PAGE_SIZE = 1000


@dataclass(frozen=True)
class Deal:
    region_code: str
    apt_name: str
    deal_amount: int          # 만원. 매매=거래금액, 전월세=보증금
    monthly_rent: int | None  # 만원. 매매=None, 전세=0, 월세=월세액
    exclusive_area: float
    floor: int
    build_year: int | None
    deal_date: date
    deal_type: str            # SALE | JEONSE | MONTHLY
    umd_nm: str
    jibun: str


def _int(text: str | None) -> int | None:
    """'260,000' → 260000. 빈 값이나 숫자가 아니면 None."""
    if not text:
        return None
    cleaned = text.strip().replace(",", "")
    try:
        return int(cleaned)
    except ValueError:
        return None


def _text(item: ET.Element, *tags: str) -> str:
    """전월세 응답은 태그가 소문자(roadnm)라 매매(roadNm)와 다릅니다. 후보를 순서대로 시도."""
    for tag in tags:
        el = item.find(tag)
        if el is not None and el.text and el.text.strip():
            return el.text.strip()
    return ""


def _parse(item: ET.Element, region_code: str, kind: str) -> Deal | None:
    apt_name = _text(item, "aptNm")
    area = _int(None) or None
    try:
        area = float(_text(item, "excluUseAr"))
    except ValueError:
        return None

    y, m, d = _int(_text(item, "dealYear")), _int(_text(item, "dealMonth")), _int(_text(item, "dealDay"))
    if not (apt_name and y and m and d):
        return None

    if kind == "SALE":
        amount, rent, deal_type = _int(_text(item, "dealAmount")), None, "SALE"
    else:
        amount = _int(_text(item, "deposit"))
        rent = _int(_text(item, "monthlyRent")) or 0
        deal_type = "JEONSE" if rent == 0 else "MONTHLY"
    if amount is None:
        return None

    return Deal(
        region_code=region_code,
        apt_name=apt_name,
        deal_amount=amount,
        monthly_rent=rent,
        exclusive_area=area,
        # 층은 지하(-1)도 있고 값이 비는 경우도 있습니다. unique 제약에 쓰이므로 NULL 을 두면 안 됩니다.
        floor=_int(_text(item, "floor")) or 0,
        build_year=_int(_text(item, "buildYear")),
        deal_date=date(y, m, d),
        deal_type=deal_type,
        umd_nm=_text(item, "umdNm"),
        jibun=_text(item, "jibun"),
    )


# 초당 요청 제한에 걸리면 200 이 아닌 429 와 함께 cmmMsgHeader 가 옵니다.
# 이 응답에는 totalCount 가 없어서, 그냥 파싱하면 "0건 수집 성공" 으로 조용히 넘어갑니다.
# 실제로 이것 때문에 한 번 속았습니다. 반드시 감지해서 쉬었다 다시 호출합니다.
RATE_LIMIT_MARKERS = (
    "LIMITED_NUMBER_OF_SERVICE_REQUESTS_PER_SECOND_EXCEEDS_ERROR",
    "LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR",
)
MAX_RETRY = 5


def _request(service_key: str, kind: str, region_code: str, year_month: str, page: int) -> str:
    """한 페이지를 받아옵니다. 초당 제한이면 점점 더 오래 쉬면서 다시 시도합니다."""
    for attempt in range(MAX_RETRY):
        r = requests.get(
            ENDPOINTS[kind],
            params={"serviceKey": service_key, "LAWD_CD": region_code,
                    "DEAL_YMD": year_month, "numOfRows": PAGE_SIZE, "pageNo": page},
            timeout=30,
        )
        body = r.text

        if any(m in body for m in RATE_LIMIT_MARKERS):
            wait = 2 ** attempt
            log.warning("요청 제한(429) — %ds 후 재시도 (%d/%d)", wait, attempt + 1, MAX_RETRY)
            time.sleep(wait)
            continue

        if "SERVICE_KEY_IS_NOT_REGISTERED_ERROR" in body:
            raise RuntimeError(
                "서비스키가 등록되지 않았습니다. .env 의 DATA_GO_KR_SERVICE_KEY 에 "
                "'디코딩' 키를 넣었는지 확인하세요."
            )
        if "SERVICE_ACCESS_DENIED_ERROR" in body:
            raise RuntimeError("이 API 를 활용신청했는지 확인하세요(접근 거부).")

        r.raise_for_status()
        return body

    raise RuntimeError(
        f"요청 제한으로 {MAX_RETRY}회 재시도했지만 실패했습니다 "
        f"({kind} {region_code} {year_month}). 동시 실행을 줄이거나 잠시 후 다시 시도하세요."
    )


def fetch(service_key: str, kind: str, region_code: str, year_month: str,
          delay: float = 0.3) -> list[Deal]:
    """지정한 시군구·월의 거래를 전부 가져옵니다. kind 는 SALE 또는 RENT."""
    deals: list[Deal] = []
    page = 1
    while True:
        body = _request(service_key, kind, region_code, year_month, page)

        root = ET.fromstring(body)
        code_el = root.find(".//resultCode")
        if code_el is not None and code_el.text and code_el.text.strip().lstrip("0"):
            msg = root.find(".//resultMsg")
            raise RuntimeError(f"API 오류 {code_el.text}: {msg.text if msg is not None else ''}")

        # totalCount 가 아예 없으면 정상 응답이 아닙니다. 0건으로 착각하지 않도록 막습니다.
        total_el = root.find(".//totalCount")
        if total_el is None:
            raise RuntimeError(
                f"totalCount 가 없는 응답입니다({kind} {region_code} {year_month}). "
                f"응답 앞부분: {' '.join(body.split())[:160]}"
            )

        items = root.findall(".//item")
        for item in items:
            deal = _parse(item, region_code, kind)
            if deal:
                deals.append(deal)

        total = _int(total_el.text) or 0
        if page * PAGE_SIZE >= total or not items:
            break
        page += 1
        time.sleep(delay)

    log.debug("  %s %s %s → %d건", kind, region_code, year_month, len(deals))
    return deals
