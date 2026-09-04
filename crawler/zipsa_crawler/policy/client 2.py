"""청년정책 수집 — 온통청년 공식 OpenAPI.

    GET https://www.youthcenter.go.kr/go/ythip/getPlcy
        ?apiKeyNm=<키>&pageNum=1&pageSize=500&rtnType=json

인증키는 youthcenter.go.kr 로그인 > 마이페이지 > OPEN API 에서 발급받는다(담당자 승인 필요).
⚠️ 파라미터명이 serviceKey 가 아니라 apiKeyNm 이다.
   키가 틀리면 {"errorCode":"e001","errorMsg":"invalid api key."} 가 온다.
"""

from __future__ import annotations

import html
import logging
import re
import time
from dataclasses import dataclass
from datetime import date, datetime

import requests

log = logging.getLogger("zipsa.crawler.policy")

URL = "https://www.youthcenter.go.kr/go/ythip/getPlcy"
PAGE_SIZE = 500          # 500까지 확인됨. 2,750건이 6번이면 끝난다.

# 시·도 코드 → 회원의 region 값. users.region 이 시·도명이라 코드로 맞춰준다.
SIDO = {
    "11": "서울", "26": "부산", "27": "대구", "28": "인천",
    # 2026년 개편: 광주광역시(29) + 전라남도(46) → 전남광주통합특별시(12).
    # 구 코드로 조회하면 0건이 나온다. 실거래가 API 도 마찬가지다.
    "12": "전남광주",
    "29": "전남광주", "46": "전남광주",
    "30": "대전", "31": "울산", "36": "세종", "41": "경기",
    "42": "강원", "51": "강원",
    "43": "충북", "44": "충남",
    "45": "전북", "52": "전북",
    "47": "경북", "48": "경남", "50": "제주",
}
# 시·도가 이만큼 넘게 걸리면 사실상 전국 사업이다.
NATIONWIDE_THRESHOLD = 15

# 이 서비스는 청년 주거 서비스다. 전체 2,750건 중 주거·금융 관련만 담는다.
KEEP_LCLSF = ("주거", "금융")
HOUSING_KEYWORD = re.compile(
    r"주거|전세|월세|임대|보증금|주택|청약|분양|이사|기숙사|LTV|디딤돌|버팀목|중기청")


@dataclass(frozen=True)
class Policy:
    external_id: str
    title: str
    content: str | None
    category: str
    region: str | None
    issuer: str | None
    target_job: str | None
    target_age_range: str | None
    target_salary_range: str | None
    target_min_age: int | None
    target_max_age: int | None
    earn_min_amt: int | None
    earn_max_amt: int | None
    marital_condition: str | None
    keyword: str | None
    apply_start_date: date | None
    apply_end_date: date | None
    apply_method: str | None
    source_url: str
    zip_codes: str | None
    sido_codes: str | None


def _clean(raw: str | None) -> str:
    if not raw:
        return ""
    return re.sub(r"[ \t]+", " ", html.unescape(str(raw))).strip()


def _int(v) -> int | None:
    try:
        n = int(str(v).strip())
    except (TypeError, ValueError):
        return None
    # 0 이나 99999 는 "제한 없음" 을 뜻하는 자리표시자다.
    return None if n in (0, 99999) else n


def _period(raw: str | None) -> tuple[date | None, date | None]:
    """'20260701 ~ 20261117' 을 두 날짜로. 상시면 둘 다 None."""
    if not raw:
        return None, None
    found = re.findall(r"\d{8}", raw)
    def parse(s: str) -> date | None:
        try:
            return datetime.strptime(s, "%Y%m%d").date()
        except ValueError:
            return None
    if len(found) >= 2:
        return parse(found[0]), parse(found[1])
    if len(found) == 1:
        return parse(found[0]), None
    return None, None


def _regions(zip_codes: str | None) -> tuple[str | None, str | None, str | None]:
    """zipCd → (원본, 시·도코드 목록, 표시용 지역명).

    기관명 문자열에서 지역을 긁던 방식은 "주택과" 같은 값에서 샜다.
    코드로 비교하면 그런 문제가 없다.
    """
    if not zip_codes:
        return None, None, None
    codes = [c.strip() for c in str(zip_codes).split(",") if c.strip()]
    sidos = sorted({c[:2] for c in codes if len(c) >= 2})
    if not sidos:
        return zip_codes, None, None
    if len(sidos) >= NATIONWIDE_THRESHOLD:
        return zip_codes, "ALL", "전국"
    names = sorted({SIDO[s] for s in sidos if s in SIDO})
    return zip_codes, ",".join(sidos), ("·".join(names) if names else None)


def _category(title: str, content: str, keyword: str, mclsf: str) -> str:
    blob = f"{title} {keyword} {mclsf} {content}"
    for text in (f"{title} {keyword} {mclsf}", blob):
        if re.search(r"임대주택|공공임대|행복주택|매입임대|전세임대|국민임대|영구임대|기숙사", text):
            return "PUBLIC_HOUSING"
        if re.search(r"분양|청약|신혼희망타운|주택공급", text):
            return "SUPPLY"
        if re.search(r"대출|융자|이자지원|버팀목|디딤돌|햇살론|보증료", text):
            return "LOAN"
    return "HOUSING"


def parse(item: dict) -> Policy | None:
    plcy_no = _clean(item.get("plcyNo"))
    title = _clean(item.get("plcyNm"))
    if not plcy_no or not title:
        return None

    lclsf = _clean(item.get("lclsfNm"))
    mclsf = _clean(item.get("mclsfNm"))
    keyword = _clean(item.get("plcyKywdNm"))
    explain = _clean(item.get("plcyExplnCn"))
    support = _clean(item.get("plcySprtCn"))
    content = "\n\n".join(p for p in (explain, support) if p) or None

    # 주거 분야가 아니면 제목·키워드에 주거 관련 표현이 있을 때만 담는다.
    if not any(k in lclsf for k in KEEP_LCLSF):
        if not HOUSING_KEYWORD.search(f"{title} {keyword} {mclsf}"):
            return None

    zip_codes, sido_codes, region = _regions(item.get("zipCd"))
    min_age, max_age = _int(item.get("sprtTrgtMinAge")), _int(item.get("sprtTrgtMaxAge"))
    earn_min, earn_max = _int(item.get("earnMinAmt")), _int(item.get("earnMaxAmt"))
    start, end = _period(item.get("aplyYmd"))

    return Policy(
        external_id=plcy_no[:100],
        title=title[:255],
        content=content,
        category=_category(title, content or "", keyword, mclsf),
        region=(region or _clean(item.get("sprvsnInstCdNm")))[:50] or None,
        issuer=_clean(item.get("sprvsnInstCdNm"))[:100] or None,
        target_job=_clean(item.get("addAplyQlfcCndCn"))[:255] or None,
        target_age_range=(f"{min_age}~{max_age}세" if (min_age and max_age) else "제한없음")[:255],
        target_salary_range=(_clean(item.get("earnEtcCn"))
                             or (f"{earn_min}~{earn_max}" if (earn_min or earn_max) else "제한없음"))[:255],
        target_min_age=min_age,
        target_max_age=max_age,
        earn_min_amt=earn_min,
        earn_max_amt=earn_max,
        # 결혼 조건은 코드로 온다. 0055003 = 제한없음.
        marital_condition={"0055001": "기혼", "0055002": "미혼"}.get(
            _clean(item.get("mrgSttsCd")), "제한없음"),
        keyword=keyword[:100] or None,
        apply_start_date=start,
        apply_end_date=end,
        apply_method=_clean(item.get("plcyAplyMthdCn")) or None,
        source_url=(_clean(item.get("refUrlAddr1"))
                    or f"https://www.youthcenter.go.kr/youthPolicy/ythPlcyTotalSearch"
                       f"/ythPlcyDetail/{plcy_no}")[:500],
        zip_codes=zip_codes,
        sido_codes=sido_codes,
    )


def fetch(api_key: str, delay: float = 0.4) -> list[Policy]:
    if not api_key or not api_key.strip():
        raise RuntimeError(
            "YOUTH_CENTER_API_KEY 가 비어 있습니다. .env 를 확인하세요. "
            "(youthcenter.go.kr > 마이페이지 > OPEN API 에서 발급)")

    session = requests.Session()
    out: dict[str, Policy] = {}
    page = 1

    while True:
        r = session.get(URL, params={
            "apiKeyNm": api_key, "pageNum": page,
            "pageSize": PAGE_SIZE, "rtnType": "json",
        }, timeout=30)
        r.raise_for_status()
        body = r.json()

        if body.get("errorCode"):
            raise RuntimeError(
                f"온통청년 API 오류 {body['errorCode']}: {body.get('errorMsg')} "
                "(인증키 승인 상태와 유효기간을 확인하세요)")

        result = body.get("result") or {}
        items = result.get("youthPolicyList") or []
        if not items:
            break

        for item in items:
            policy = parse(item)
            if policy:
                out[policy.external_id] = policy

        total = int((result.get("pagging") or {}).get("totCount") or 0)
        log.info("  %d/%d 페이지 (누적 주거·금융 %d건)",
                 page, max(1, -(-total // PAGE_SIZE)), len(out))
        if page * PAGE_SIZE >= total:
            break
        page += 1
        time.sleep(delay)

    return list(out.values())
