"""주소 → 좌표 변환 (카카오 로컬 API).

국토부 실거래가 API 는 좌표를 주지 않습니다. 지도에 마커를 찍으려면
지번 주소를 좌표로 바꿔야 합니다.

호출을 아끼는 게 중요합니다. 같은 아파트의 거래가 수십 건씩 나오는데
매번 물어보면 금방 한도에 걸립니다. 그래서 두 겹으로 캐시합니다.
  1) 이미 DB 에 좌표가 있는 아파트는 그대로 재사용
  2) 실행 중에는 메모리 캐시
"""

from __future__ import annotations

import logging
import time

import requests

log = logging.getLogger("zipsa.crawler.geocode")

URL = "https://dapi.kakao.com/v2/local/search/address.json"
KEYWORD_URL = "https://dapi.kakao.com/v2/local/search/keyword.json"


class Geocoder:
    def __init__(self, rest_key: str, delay: float = 0.05) -> None:
        self._key = rest_key
        self._delay = delay
        self._cache: dict[str, tuple[float, float] | None] = {}
        self.hits = self.misses = self.failures = 0

    def preload(self, known: dict[str, tuple[float, float]]) -> None:
        """DB 에 이미 있는 좌표를 캐시에 밀어넣습니다."""
        self._cache.update(known)

    def lookup(self, cache_key: str, address: str, apt_name: str = "") -> tuple[float, float] | None:
        if cache_key in self._cache:
            self.hits += 1
            return self._cache[cache_key]

        self.misses += 1
        coord = self._search(URL, {"query": address})
        if coord is None and apt_name:
            # 지번 주소로 못 찾는 경우가 있습니다(신축·재건축 등). 아파트 이름으로 한 번 더.
            coord = self._search(KEYWORD_URL, {"query": f"{address.rsplit(' ', 1)[0]} {apt_name}"})
        if coord is None:
            self.failures += 1
        self._cache[cache_key] = coord
        return coord

    def _search(self, url: str, params: dict) -> tuple[float, float] | None:
        try:
            r = requests.get(url, params=params,
                             headers={"Authorization": f"KakaoAK {self._key}"}, timeout=15)
            time.sleep(self._delay)
            if r.status_code == 401:
                raise RuntimeError(
                    "카카오 인증 실패(401). .env 의 KAKAO_REST_API_KEY 가 "
                    "'REST API 키' 가 맞는지 확인하세요(JavaScript 키 아님)."
                )
            if r.status_code != 200:
                return None
            docs = r.json().get("documents") or []
            if not docs:
                return None
            return float(docs[0]["y"]), float(docs[0]["x"])
        except requests.RequestException:
            return None

    def summary(self) -> str:
        return (f"좌표 캐시 적중 {self.hits} / 신규 조회 {self.misses} / 실패 {self.failures}")
