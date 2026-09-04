"""언론사 RSS 수집.

⚠️ 크롤링이 아니라 RSS 입니다.
   RSS 는 언론사가 배포 목적으로 공개한 피드라 읽어도 됩니다.
   반면 기사 페이지를 직접 긁는 것은 robots.txt·저작권 문제가 있습니다.
   (예: 네이버 뉴스는 robots.txt 가 User-agent: * Disallow: / 로 전면 차단)

⚠️ 본문은 저장하지 않습니다.
   제목·요약(RSS description)·원문 링크만 담고, 읽기는 원문 사이트로 보냅니다.
"""

from __future__ import annotations

import html
import logging
import re
from dataclasses import dataclass
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from xml.etree import ElementTree as ET

import requests

log = logging.getLogger("zipsa.crawler.news")

# (언론사, RSS URL). 청년 주거·부동산·경제 위주로 골랐습니다.
FEEDS: list[tuple[str, str]] = [
    ("연합뉴스", "https://www.yna.co.kr/rss/economy.xml"),
    ("매일경제", "https://www.mk.co.kr/rss/50300009/"),      # 부동산
    ("한국경제", "https://www.hankyung.com/feed/realestate"),  # 부동산
    ("경향신문", "https://www.khan.co.kr/rss/rssdata/economy_news.xml"),
    ("한겨레", "https://www.hani.co.kr/rss/economy/"),
    ("동아일보", "https://rss.donga.com/economy.xml"),
]

# 이 서비스와 관련 있는 기사만 남깁니다. 경제 피드 전체를 담으면 청년 주거와 무관한
# 기사가 대부분이라 화면이 의미 없어집니다.
KEYWORDS = (
    "청년", "전세", "월세", "임대", "보증금", "주거", "주택", "아파트", "부동산",
    "분양", "청약", "LH", "행복주택", "매매", "전월세", "역세권", "재건축", "재개발",
    "대출", "디딤돌", "버팀목", "중기청", "신혼부부", "실거래",
)

MAX_SUMMARY = 200


@dataclass(frozen=True)
class Article:
    external_id: str
    title: str
    summary: str | None
    press_name: str
    source_url: str
    published_at: datetime
    # 본문. 배포하지 않는 프로젝트 전제로 채웁니다(news/article.py 주석 참고).
    content: str | None = None


def _clean(raw: str | None) -> str:
    """RSS description 에 섞인 HTML 태그와 엔티티를 걷어냅니다."""
    if not raw:
        return ""
    text = re.sub(r"<[^>]+>", " ", raw)
    text = html.unescape(text)
    return re.sub(r"\s+", " ", text).strip()


def _parse_date(raw: str | None) -> datetime:
    if not raw:
        return datetime.now(timezone.utc)
    try:
        dt = parsedate_to_datetime(raw)
    except (TypeError, ValueError):
        return datetime.now(timezone.utc)
    # naive 로 저장한다(DB 컬럼이 timestamp without time zone).
    return dt.replace(tzinfo=None) if dt.tzinfo is None else dt.astimezone(timezone.utc).replace(tzinfo=None)


def _relevant(title: str, summary: str) -> bool:
    blob = f"{title} {summary}"
    return any(k in blob for k in KEYWORDS)


def fetch_feed(press: str, url: str, user_agent: str) -> list[Article]:
    try:
        r = requests.get(url, headers={"User-Agent": user_agent}, timeout=20)
        r.raise_for_status()
        # 일부 피드가 인코딩을 잘못 알려줘서 본문에서 다시 추정하게 둡니다.
        r.encoding = r.apparent_encoding or r.encoding
        root = ET.fromstring(r.text)
    except (requests.RequestException, ET.ParseError) as e:
        log.warning("  %s 피드 실패 — %s", press, type(e).__name__)
        return []

    out: list[Article] = []
    for item in root.findall(".//item"):
        title = _clean(item.findtext("title"))
        link = (item.findtext("link") or "").strip()
        if not title or not link:
            continue

        summary = _clean(item.findtext("description"))[:MAX_SUMMARY]
        if not _relevant(title, summary):
            continue

        out.append(Article(
            # guid 가 없는 피드가 있어 링크로 대체합니다. unique 키로 쓰입니다.
            external_id=(item.findtext("guid") or link).strip()[:500],
            title=title[:500],
            summary=summary or None,
            press_name=press,
            source_url=link[:1000],
            published_at=_parse_date(item.findtext("pubDate")),
        ))
    log.info("  %-6s %3d건 수집", press, len(out))
    return out
