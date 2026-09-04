"""기사 본문 추출.

⚠️ 배포하지 않는 프로젝트라는 전제로 만든 기능입니다.
   기사 본문은 언론사 저작물입니다. 외부 공개 시에는 이 단계를 끄고
   제목·요약·원문 링크만 쓰세요(collect_news 의 with_content=False).

언론사마다 HTML 구조가 달라 ① 알려진 본문 컨테이너를 먼저 찾고
② 못 찾으면 텍스트가 가장 많은 블록을 고르는 2단 방식입니다.
"""

from __future__ import annotations

import logging
import re
import time

import requests
from lxml import html as LH

log = logging.getLogger("zipsa.crawler.news")

# 본문 컨테이너 후보. 위에서부터 시도합니다.
CONTAINERS = [
    "//div[@id='dic_area']",
    "//div[contains(@class,'article-body')]",
    "//div[contains(@class,'article_body')]",
    "//div[@id='articleBody']",
    "//div[@itemprop='articleBody']",
    "//div[contains(@class,'news_view')]",
    "//div[contains(@class,'art_body')]",
    "//article",
]
DROP = "//script|//style|//nav|//header|//footer|//aside|//figure|//iframe|//form"

# 본문에 섞여 들어오는 UI 부스러기. 기사 내용이 아닙니다.
NOISE = [
    re.compile(r"구독\s*구독중"),
    re.compile(r"이전\s+다음"),
    re.compile(r"Your browser[^\n]*"),
    re.compile(r"펼침\s*\d+:\d+"),
    re.compile(r"글씨크기\s*조절.*?$", re.M),
    re.compile(r"무단[ ]?전재.*?금지.*?$", re.M),
    re.compile(r"저작권자\s*©[^\n]*"),
    re.compile(r"^\s*(수정|입력)\s*\d{4}-\d{2}-\d{2}[^\n]*$", re.M),
]
MIN_LENGTH = 400


def _clean(text: str) -> str:
    for pattern in NOISE:
        text = pattern.sub(" ", text)
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def extract(url: str, user_agent: str, timeout: int = 20) -> str | None:
    try:
        r = requests.get(url, headers={"User-Agent": user_agent}, timeout=timeout)
        r.raise_for_status()
        r.encoding = r.apparent_encoding or r.encoding
        doc = LH.fromstring(r.text)
    except Exception:      # noqa: BLE001 — 한 기사 실패로 전체를 멈추지 않는다
        return None

    for bad in doc.xpath(DROP):
        parent = bad.getparent()
        if parent is not None:
            parent.remove(bad)

    best, best_len = "", 0
    for xpath in CONTAINERS:
        for el in doc.xpath(xpath):
            text = _clean(el.text_content())
            if len(text) > best_len:
                best, best_len = text, len(text)
        if best_len >= MIN_LENGTH:
            break

    if best_len < MIN_LENGTH:
        # 컨테이너를 못 찾으면 텍스트가 가장 많은 div 를 고릅니다.
        for el in doc.xpath("//div"):
            text = _clean(el.text_content())
            if len(text) > best_len:
                best, best_len = text, len(text)

    return best if best_len >= 200 else None


def fill_contents(articles: list, user_agent: str, delay: float = 0.8) -> int:
    """기사 목록에 본문을 채웁니다. Article 이 frozen dataclass 라 새 객체로 바꿔 돌려줍니다."""
    import dataclasses

    filled = 0
    for i, a in enumerate(articles):
        body = extract(a.source_url, user_agent)
        if body:
            articles[i] = dataclasses.replace(a, content=body)
            filled += 1
        time.sleep(delay)
        if (i + 1) % 25 == 0:
            log.info("  본문 %d/%d (성공 %d)", i + 1, len(articles), filled)
    return filled
