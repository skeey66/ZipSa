"""API 키가 실제로 동작하는지 확인합니다.

    crawler/.venv/bin/python scripts/check_keys.py

.env 에 넣은 키로 각 API 를 한 번씩 호출해보고 결과를 알려줍니다.
공공데이터포털 키는 인코딩/디코딩을 잘못 넣었을 때 그것까지 짚어줍니다.
"""

from __future__ import annotations

import os
import re
import sys
import urllib.parse
from pathlib import Path

import requests
from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parents[1] / ".env")

OK, FAIL, SKIP = "✅", "❌", "⬜"
TIMEOUT = 20


def line(mark: str, name: str, msg: str) -> None:
    print(f"  {mark} {name:32} {msg}")


def check_data_go_kr() -> None:
    key = os.getenv("DATA_GO_KR_SERVICE_KEY", "").strip()
    targets = {
        "아파트 매매 실거래가": "https://apis.data.go.kr/1613000/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev",
        "아파트 전월세 실거래가": "https://apis.data.go.kr/1613000/RTMSDataSvcAptRent/getRTMSDataSvcAptRent",
    }
    if not key:
        for n in targets:
            line(SKIP, n, "DATA_GO_KR_SERVICE_KEY 가 비어 있음")
        return

    # 인코딩 키를 넣었는지 먼저 짚어준다. 이걸 모르면 "등록되지 않은 서비스키" 에서 몇 시간 날린다.
    if "%" in key:
        decoded = urllib.parse.unquote(key)
        print(f"  ⚠️  인코딩 키로 보입니다(% 포함). 마이페이지의 '디코딩' 키로 바꾸세요.")
        print(f"      참고 — 디코딩하면: {decoded[:20]}...{decoded[-6:]}")
        print()

    for name, url in targets.items():
        try:
            r = requests.get(
                url,
                params={"serviceKey": key, "LAWD_CD": "11110", "DEAL_YMD": "202608",
                        "numOfRows": "1", "pageNo": "1"},
                timeout=TIMEOUT,
            )
            body = r.text
            if "SERVICE_KEY_IS_NOT_REGISTERED_ERROR" in body:
                line(FAIL, name, "등록되지 않은 서비스키 (인코딩 키를 넣었거나 활용신청 미승인)")
            elif "SERVICE_ACCESS_DENIED_ERROR" in body:
                line(FAIL, name, "접근 거부 — 이 API 를 활용신청했는지 확인하세요")
            elif "LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS" in body:
                line(FAIL, name, "일일 호출 한도 초과")
            # 성공 코드는 API 마다 000 / 00 으로 갈립니다. 앞자리만 보고 판단하면 오탐이 납니다.
            elif re.search(r"<resultCode>0{2,3}</resultCode>", body) or '"resultCode":"00' in body:
                total = re.search(r"<totalCount>(\d+)</totalCount>", body)
                line(OK, name, f"정상 (서울 종로구 2026-08 → {total.group(1)}건)" if total else "정상")
            else:
                snippet = " ".join(body.split())[:90]
                line(FAIL, name, f"예상 밖 응답: {snippet}")
        except requests.RequestException as e:
            line(FAIL, name, f"요청 실패: {e.__class__.__name__}")


def check_youth_center() -> None:
    key = os.getenv("YOUTH_CENTER_API_KEY", "").strip()
    name = "온통청년 청년정책"
    if not key:
        line(SKIP, name, "YOUTH_CENTER_API_KEY 가 비어 있음")
        return
    try:
        r = requests.get(
            "https://www.youthcenter.go.kr/go/ythip/getPlcy",
            params={"apiKeyNm": key, "pageNum": "1", "pageSize": "1", "rtnType": "json"},
            timeout=TIMEOUT,
        )
        if "invalid api key" in r.text:
            line(FAIL, name, "인증키가 거부됨 (승인 여부 확인)")
        elif r.status_code == 200:
            line(OK, name, "정상")
        else:
            line(FAIL, name, f"HTTP {r.status_code}")
    except requests.RequestException as e:
        line(FAIL, name, f"요청 실패: {e.__class__.__name__}")


def check_kakao() -> None:
    key = os.getenv("KAKAO_REST_API_KEY", "").strip()
    name = "카카오 주소→좌표"
    if not key:
        line(SKIP, name, "KAKAO_REST_API_KEY 가 비어 있음")
        return
    try:
        r = requests.get(
            "https://dapi.kakao.com/v2/local/search/address.json",
            params={"query": "서울특별시 중구 세종대로 110"},
            headers={"Authorization": f"KakaoAK {key}"},
            timeout=TIMEOUT,
        )
        if r.status_code == 401:
            line(FAIL, name, "401 — REST API 키가 아니거나 잘못됨 (JavaScript 키를 넣었을 수 있음)")
        elif r.status_code == 200 and r.json().get("documents"):
            d = r.json()["documents"][0]
            line(OK, name, f"정상 (서울시청 → {d['y'][:8]}, {d['x'][:9]})")
        else:
            line(FAIL, name, f"HTTP {r.status_code}")
    except requests.RequestException as e:
        line(FAIL, name, f"요청 실패: {e.__class__.__name__}")


def check_openai() -> None:
    """실제로 호출해봅니다. 키 형식만 보면 폐기된 키를 못 걸러냅니다."""
    key = os.getenv("OPENAI_API_KEY", "").strip()
    name = "OpenAI (AI 요약)"
    if not key:
        line(SKIP, name, "OPENAI_API_KEY 가 비어 있음 (없어도 나머지는 동작)")
        return
    try:
        r = requests.get("https://api.openai.com/v1/models",
                         headers={"Authorization": f"Bearer {key}"}, timeout=TIMEOUT)
        if r.status_code == 401:
            line(FAIL, name, "401 — 키가 잘못됐거나 폐기됨")
        elif r.status_code == 429:
            line(FAIL, name, "429 — 크레딧 소진 또는 요청 한도 초과")
        elif r.status_code == 200:
            want = os.getenv("OPENAI_MODEL", "gpt-4o-mini").strip()
            ids = {m["id"] for m in r.json().get("data", [])}
            if want in ids:
                line(OK, name, f"정상 (모델 {want} 사용 가능)")
            else:
                line(FAIL, name, f"키는 정상이나 모델 '{want}' 에 접근할 수 없습니다")
        else:
            line(FAIL, name, f"HTTP {r.status_code}")
    except requests.RequestException as e:
        line(FAIL, name, f"요청 실패: {e.__class__.__name__}")


def check_local() -> None:
    js = os.getenv("VITE_KAKAO_MAP_KEY", "").strip()
    line(OK if js else SKIP, "카카오 지도 JS 키",
         "설정됨 (실제 동작은 브라우저에서 확인)" if js else "VITE_KAKAO_MAP_KEY 가 비어 있음")
    # (AI 키는 check_openai 에서 실제 호출로 확인합니다)


def main() -> int:
    print("\n.env 의 API 키를 실제로 호출해서 확인합니다...\n")
    check_data_go_kr()
    check_youth_center()
    check_kakao()
    check_openai()
    check_local()
    print("\n  ❌ 가 있으면 해당 줄의 안내를 먼저 확인하세요.\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
