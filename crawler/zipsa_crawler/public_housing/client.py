"""공공임대 수집 클라이언트.

두 개의 서로 다른 API 를 씁니다.

  ① LH 분양임대공고문      → 「모집 공고」  (공고명·모집기간·신청링크)
  ② 마이홈포털 단지정보    → 「단지」      (주소·세대수·면적·보증금·월세)

둘은 공통 키가 없어 조인하지 않습니다. 각각 다른 표에 적재합니다.
"""

from __future__ import annotations

import logging
import time
from dataclasses import dataclass
from datetime import date, datetime

import requests

log = logging.getLogger("zipsa.crawler.housing")

LH_URL = "https://apis.data.go.kr/B552555/lhLeaseNoticeInfo1/lhLeaseNoticeInfo1"
MYHOME_URL = "https://apis.data.go.kr/1613000/HWSPR04/rentalHouseGwList"

# 공급유형 한글 → 코드. 매핑에 없으면 OTHER 로 두고 원문은 잃지 않습니다.
HOUSING_TYPE = {
    "행복주택": "HAPPY_HOUSE",
    "국민임대": "NATIONAL_RENTAL",
    "영구임대": "PERMANENT_RENTAL",
    "매입임대": "PURCHASE_RENTAL",
    "전세임대": "JEONSE_RENTAL",
    "통합공공임대": "INTEGRATED_RENTAL",
    "공공임대": "PUBLIC_RENTAL",
    "장기전세": "LONG_JEONSE",
    "공공지원민간임대": "PUBLIC_SUPPORT_PRIVATE",
}

# 공고 중 주택이 아닌 것(토지·상가 등)은 이 서비스와 무관합니다.
NOTICE_SKIP = ("토지", "상가", "주차장", "창고")


def housing_type_of(korean: str | None) -> str:
    if not korean:
        return "OTHER"
    return HOUSING_TYPE.get(korean.strip(), "OTHER")


# ────────────────────────── ① LH 공고 ──────────────────────────

@dataclass(frozen=True)
class Notice:
    external_id: str
    name: str
    housing_type: str
    housing_type_name: str
    region: str
    recruit_start_date: date
    recruit_end_date: date
    apply_url: str
    source_url: str
    status: str


def _parse_dot_date(raw: str | None) -> date | None:
    """'2026.09.03' 또는 '20260903' 둘 다 받습니다."""
    if not raw:
        return None
    cleaned = raw.strip().replace(".", "").replace("-", "")
    try:
        return datetime.strptime(cleaned, "%Y%m%d").date()
    except ValueError:
        return None


def fetch_notices(service_key: str, start: date, end: date, page_size: int = 100,
                  delay: float = 0.3) -> list[Notice]:
    """모집 공고를 전부 가져옵니다.

    ⚠️ 파라미터 이름을 틀리면 에러가 아니라 SS_CODE='Y' 에 빈 배열이 옵니다.
       (성공인 척하면서 0건) 필수는 PG_SZ · PAGE · PAN_NT_ST_DT · CLSG_DT 입니다.
    """
    out: list[Notice] = []
    page = 1
    while True:
        r = requests.get(LH_URL, params={
            "serviceKey": service_key,
            "PG_SZ": page_size,
            "PAGE": page,
            "PAN_NT_ST_DT": start.strftime("%Y.%m.%d"),
            "CLSG_DT": end.strftime("%Y.%m.%d"),
        }, timeout=30)
        r.raise_for_status()
        body = r.json()

        rows: list[dict] = []
        for block in body if isinstance(body, list) else []:
            if isinstance(block, dict) and "dsList" in block:
                rows = block["dsList"] or []
        if not rows:
            break

        total = int(rows[0].get("ALL_CNT") or 0)
        for row in rows:
            type_name = (row.get("AIS_TP_CD_NM") or "").strip()
            if any(skip in type_name for skip in NOTICE_SKIP):
                continue
            st = _parse_dot_date(row.get("PAN_NT_ST_DT"))
            ed = _parse_dot_date(row.get("CLSG_DT"))
            pan_id = (row.get("PAN_ID") or "").strip()
            url = (row.get("DTL_URL") or "").strip()
            if not (pan_id and st and ed and url):
                continue
            out.append(Notice(
                external_id=f"LH-{pan_id}",
                name=(row.get("PAN_NM") or "").strip()[:255],
                housing_type=housing_type_of(type_name),
                housing_type_name=type_name,
                region=(row.get("CNP_CD_NM") or "").strip()[:50],
                recruit_start_date=st,
                recruit_end_date=ed,
                apply_url=url[:500],
                source_url=url[:500],
                status=(row.get("PAN_SS") or "").strip(),
            ))

        log.info("  공고 %d/%d 페이지 (누적 %d건)", page, max(1, -(-total // page_size)), len(out))
        if page * page_size >= total:
            break
        page += 1
        time.sleep(delay)
    return out


# ────────────────────────── ② 마이홈 단지 ──────────────────────────

@dataclass(frozen=True)
class Complex:
    external_id: str
    complex_no: int
    name: str
    institution: str | None
    sido_code: str
    sido_name: str | None
    sigungu_code: str
    sigungu_name: str | None
    road_address: str | None
    housing_type: str
    house_type: str | None
    style_name: str | None
    household_count: int | None
    exclusive_area: float | None
    supply_area: float | None
    deposit: int | None
    monthly_rent: int | None
    parking_count: int | None
    completed_date: str | None


def _num(v, cast=int):
    try:
        return cast(v)
    except (TypeError, ValueError):
        return None


def fetch_complexes(service_key: str, sido_code: str, sigungu_code: str,
                    page_size: int = 500, delay: float = 0.2) -> list[Complex]:
    """한 시군구의 단지를 전부 가져옵니다.

    ⚠️ signguCode 는 법정동코드 5자리가 아니라 뒤 3자리입니다(강남구=680).
       5자리로 보내면 오류가 아니라 NODATA_ERROR 가 옵니다.
    """
    out: list[Complex] = []
    page = 1
    while True:
        r = requests.get(MYHOME_URL, params={
            "serviceKey": service_key,
            "brtcCode": sido_code,
            "signguCode": sigungu_code,
            "numOfRows": page_size,
            "pageNo": page,
        }, timeout=30)
        r.raise_for_status()
        res = r.json().get("response", {})
        header = res.get("header", {})
        code = header.get("resultCode")

        if code == "03":       # NODATA_ERROR — 그 지역에 단지가 없을 뿐
            break
        if code != "00":
            raise RuntimeError(f"마이홈 API 오류 {code}: {header.get('resultMsg')}")

        body = res.get("body", {}) or {}
        items = body.get("item") or []
        if isinstance(items, dict):
            items = [items]
        if not items:
            break

        for it in items:
            sn = _num(it.get("hsmpSn"))
            name = (it.get("hsmpNm") or "").strip()
            if not sn or not name:
                continue
            style = (it.get("styleNm") or "").strip()
            out.append(Complex(
                # 한 단지가 평형마다 임대조건이 달라 평형까지 키에 넣습니다.
                external_id=f"MYHOME-{sn}-{style or 'NA'}"[:120],
                complex_no=sn,
                name=name[:255],
                institution=(it.get("insttNm") or "").strip()[:100] or None,
                sido_code=str(it.get("brtcCode") or sido_code)[:2],
                sido_name=(it.get("brtcNm") or "").strip()[:50] or None,
                sigungu_code=str(it.get("signguCode") or sigungu_code)[:3],
                sigungu_name=(it.get("signguNm") or "").strip()[:50] or None,
                road_address=(it.get("rnAdres") or "").strip()[:255] or None,
                housing_type=housing_type_of(it.get("suplyTyNm")),
                house_type=(it.get("houseTyNm") or "").strip()[:30] or None,
                style_name=style[:50] or None,
                household_count=_num(it.get("hshldCo")),
                exclusive_area=_num(it.get("suplyPrvuseAr"), float),
                supply_area=_num(it.get("suplyCmnuseAr"), float),
                deposit=_num(it.get("bassRentGtn")),
                monthly_rent=_num(it.get("bassMtRntchrg")),
                parking_count=_num(it.get("parkngCo")),
                completed_date=(it.get("competDe") or "").strip()[:8] or None,
            ))

        total = int(body.get("totalCount") or 0)
        if page * page_size >= total:
            break
        page += 1
        time.sleep(delay)
    return out
