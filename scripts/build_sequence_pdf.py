# -*- coding: utf-8 -*-
"""시퀀스 다이어그램 SVG 5장 + PDF 생성.

    python3 scripts/build_sequence_pdf.py

  · docs/diagrams/seq-*.svg   — 낱장 (A4 가로 1장 = 1123×794px @96dpi)
  · docs/ZipSa-시퀀스다이어그램.pdf — 표지 + 5장

왜 글자를 패스로 바꾸나:
  헤드리스 Chrome 은 한글 폰트를 Type 3 글리프로 박는다. 뷰어에 따라 이게
  깨져 보인다(Preview·Acrobat·pdf.js 제각각). PDF 용 사본만 글자를 아웃라인
  패스로 변환해 어디서 열어도 같게 보이게 한다. 대신 PDF 안에서 글자 검색은
  안 된다 — 검색이 필요하면 docs/diagrams/*.svg 를 보면 된다.

  docs/diagrams/*.svg 는 <text> 그대로라 편집·검색이 되고,
  PDF 만 아웃라인이다.
"""
from __future__ import annotations

import pathlib
import shutil
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
DIA = ROOT / "docs/diagrams"
PDF = ROOT / "docs/ZipSa-시퀀스다이어그램.pdf"
TTC = "/System/Library/Fonts/AppleSDGothicNeo.ttc"
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

# ── 페이지 · 눈금 ──────────────────────────────────────────
W, H = 1123, 794          # A4 가로 @96dpi
M = 34                    # 바깥 여백
ROW = 24                  # 메시지 한 줄
NOTE_H = 17
SEC_H = 26
HEAD_H = 34               # 참여자 박스
FONT = "'Apple SD Gothic Neo','Noto Sans KR',sans-serif"

INK, BODY, MUTE = "#111927", "#3A4658", "#7C899B"
LINE, RET = "#4A5568", "#8A97A8"

PAL = {
    "slate":  ("#EDF1F7", "#5B6B80"),
    "blue":   ("#E6EEFC", "#3B6FD4"),
    "green":  ("#E3F2E9", "#2E8B57"),
    "amber":  ("#FCF1DD", "#B07714"),
    "violet": ("#EDEAFB", "#6455CC"),
    "rose":   ("#FBE9EC", "#C2415C"),
    "teal":   ("#E1F1F3", "#28808A"),
}


def esc(s: str) -> str:
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


class Glyphs:
    """Apple SD Gothic Neo 에서 글리프 윤곽선과 실제 폭을 꺼낸다.

    폭을 어림하지 않고 hmtx 에서 그대로 읽으므로 가운데 정렬이 정확하다.
    """

    FACES = {400: 0, 700: 6}

    def __init__(self):
        from fontTools.ttLib import TTCollection
        c = TTCollection(TTC)
        self.f = {w: c.fonts[i] for w, i in self.FACES.items()}
        self.gs = {w: f.getGlyphSet() for w, f in self.f.items()}
        self.cmap = {w: f.getBestCmap() for w, f in self.f.items()}
        self.upem = {w: f["head"].unitsPerEm for w, f in self.f.items()}
        self.defs: dict[str, str] = {}
        self._cache: dict[tuple, tuple] = {}

    def _glyph(self, w: int, ch: str):
        key = (w, ch)
        if key in self._cache:
            return self._cache[key]
        from fontTools.pens.svgPathPen import SVGPathPen
        name = self.cmap[w].get(ord(ch))
        if name is None:
            self._cache[key] = (None, self.upem[w] * 0.5)
            return self._cache[key]
        adv = self.f[w]["hmtx"][name][0]
        gid = f"g{w}-{abs(hash(name)) % (1 << 30)}"
        if gid not in self.defs:
            pen = SVGPathPen(self.gs[w])
            self.gs[w][name].draw(pen)
            d = pen.getCommands()
            if not d:
                self._cache[key] = (None, adv)
                return self._cache[key]
            self.defs[gid] = d
        self._cache[key] = (gid, adv)
        return self._cache[key]

    def width(self, s: str, size: float, weight: int) -> float:
        w = 700 if weight == 700 else 400
        k = size / self.upem[w]
        return sum(self._glyph(w, ch)[1] for ch in s) * k

    def path(self, x, y, s, size, fill, anchor, weight, halo):
        w = 700 if weight == 700 else 400
        k = size / self.upem[w]
        total = self.width(s, size, w)
        if anchor == "middle":
            x -= total / 2
        elif anchor == "end":
            x -= total
        uses, cur = [], 0.0
        for ch in s:
            gid, adv = self._glyph(w, ch)
            if gid:
                uses.append(f'<use href="#{gid}" x="{cur:.0f}"/>')
            cur += adv
        if not uses:
            return ""
        inner = "".join(uses)
        g = f'<g transform="translate({x:.2f} {y:.2f}) scale({k:.5f} {-k:.5f})"'
        out = ""
        if halo:
            out += (f'{g} fill="none" stroke="#FFFFFF" stroke-width="{3.2 / k:.0f}" '
                    f'stroke-linejoin="round">{inner}</g>')
        return out + f'{g} fill="{fill}">{inner}</g>'


GL = Glyphs()
OUTLINE = False          # PDF 를 뽑을 때만 True


def tw(s: str, size: float, weight: int = 400) -> float:
    return GL.width(s, size, weight)


def text(x, y, s, size=8.5, fill=BODY, anchor="start", weight=None, halo=False, mono=False):
    if OUTLINE:
        return GL.path(x, y, s, size, fill, anchor, weight or 400, halo)
    a = f' text-anchor="{anchor}"' if anchor != "start" else ""
    w = f' font-weight="{weight}"' if weight else ""
    h = (' stroke="#FFFFFF" stroke-width="3.2" stroke-linejoin="round"'
         ' paint-order="stroke fill"') if halo else ""
    ls = ' letter-spacing="0.02em"' if mono else ""
    return (f'<text x="{x:.1f}" y="{y:.1f}" font-size="{size}" fill="{fill}"'
            f'{a}{w}{h}{ls}>{esc(s)}</text>')


# ── 다이어그램 정의 ────────────────────────────────────────
# lane  : (이름, 부제, 색)
# step  : ("sec", 제목)                     구간 띠
#         ("msg", from, to, 라벨)           실선 화살표
#         ("ret", from, to, 라벨)           점선 회신
#         ("self", lane, 라벨)              자기 호출
#         ("note", lane, 라벨)              메모
#         ("frag", 종류, 조건) … ("else", 조건) … ("end",)
DIAGRAMS = [
    dict(
        title="전체 동작 개요",
        lead="수집(배치)과 조회(요청)가 PostgreSQL 을 사이에 두고 분리된다. 크롤러는 백엔드를 거치지 않는다.",
        src="crawler/zipsa_crawler/main.py · backend 전 계층 · frontend/src/api/client.js",
        lanes=[("사용자", "브라우저", "slate"), ("Vue 3 SPA", "Vite · Pinia", "blue"),
               ("Spring Boot", "com.zipsa", "green"), ("OpenAI", "Spring AI", "rose"),
               ("PostgreSQL", "Flyway V1~V21", "amber"), ("Python 크롤러", "zipsa_crawler", "violet"),
               ("공공 API", "국토부 · LH · 카카오", "teal")],
        steps=[
            ("sec", "① 수집 — 사용자 요청과 무관하게 미리 돌아간다"),
            ("msg", "Python 크롤러", "공공 API", "정책 · 공고 · 실거래가 요청"),
            ("ret", "공공 API", "Python 크롤러", "JSON / XML"),
            ("msg", "Python 크롤러", "PostgreSQL", "UPSERT + crawl_jobs 기록"),
            ("note", "Python 크롤러", "백엔드를 거치지 않고 DB 에 직접 적재한다"),
            ("sec", "② 조회 — 백엔드가 유일한 외부 진입점"),
            ("msg", "사용자", "Vue 3 SPA", "화면 진입 · 검색"),
            ("msg", "Vue 3 SPA", "Spring Boot", "GET /api/… (Bearer accessToken)"),
            ("msg", "Spring Boot", "PostgreSQL", "JPA 조회 (페이징 · 필터)"),
            ("ret", "PostgreSQL", "Spring Boot", "ResultSet"),
            ("frag", "opt", "AI 요약이 필요한 화면 & OPENAI_API_KEY 존재"),
            ("msg", "Spring Boot", "OpenAI", "ChatClient 프롬프트"),
            ("ret", "OpenAI", "Spring Boot", "요약 · 적용 분석"),
            ("end",),
            ("ret", "Spring Boot", "Vue 3 SPA", "{ success, data, error }"),
            ("ret", "Vue 3 SPA", "사용자", "렌더링"),
        ],
    ),
    dict(
        title="회원가입 · 로그인 — 토큰 발급",
        lead="비밀번호는 BCrypt 로만 저장한다. Access 는 메모리, Refresh 는 localStorage.",
        src="auth/AuthController.java · auth/AuthService.java · auth/jwt/JwtTokenProvider.java",
        lanes=[("사용자", "", "slate"), ("Signup / Login", "*.vue", "blue"),
               ("AuthController", "/api/auth", "green"), ("AuthService", "@Transactional", "violet"),
               ("JwtTokenProvider", "HS256", "rose"), ("PostgreSQL", "users · refresh_tokens", "amber")],
        steps=[
            ("msg", "사용자", "Signup / Login", "아이디 입력"),
            ("msg", "Signup / Login", "AuthController", "GET /api/auth/check-id"),
            ("msg", "AuthController", "PostgreSQL", "existsByLoginId"),
            ("ret", "AuthController", "Signup / Login", "{ available: true }"),
            ("sec", "가입"),
            ("msg", "Signup / Login", "AuthController", "POST /api/auth/signup"),
            ("self", "AuthService", "중복 · 비밀번호 길이 검증 → 409"),
            ("note", "AuthService", "BCrypt 는 72바이트 초과분을 잘라내므로 길이를 먼저 막는다"),
            ("msg", "AuthService", "PostgreSQL", "INSERT users (BCrypt 해시)"),
            ("ret", "AuthController", "Signup / Login", "201 Created"),
            ("sec", "로그인"),
            ("msg", "Signup / Login", "AuthController", "POST /api/auth/login"),
            ("msg", "AuthService", "PostgreSQL", "findByLoginId"),
            ("self", "AuthService", "matches() → 그 다음 계정 상태 검사"),
            ("note", "AuthService", "순서가 중요하다. 상태를 먼저 보면 계정 존재가 새어나간다(계정 열거)"),
            ("msg", "AuthService", "JwtTokenProvider", "createAccessToken / createRefreshToken"),
            ("ret", "JwtTokenProvider", "AuthService", "JWT 2종"),
            ("msg", "AuthService", "PostgreSQL", "INSERT refresh_tokens"),
            ("ret", "AuthController", "Signup / Login", "200 { accessToken, refreshToken }"),
            ("note", "Signup / Login", "access 는 메모리에만, refresh 만 localStorage 에 남긴다"),
        ],
    ),
    dict(
        title="인증 요청 — Access Token 만료와 자동 재발급",
        lead="필터가 만료를 즉시 401 로 끊고, 프론트 인터셉터가 딱 한 번 재발급한 뒤 원요청을 재시도한다.",
        src="auth/jwt/JwtAuthenticationFilter.java · AuthService#reissue · frontend/src/api/client.js",
        lanes=[("사용자", "", "slate"), ("api/client.js", "인터셉터", "blue"),
               ("JwtAuthFilter", "OncePerRequest", "teal"), ("PolicyController", "/api/policies", "green"),
               ("AuthService", "reissue()", "violet"), ("PostgreSQL", "refresh_tokens", "amber")],
        steps=[
            ("msg", "사용자", "api/client.js", "맞춤 정책 보기"),
            ("msg", "api/client.js", "JwtAuthFilter", "GET /api/policies/recommend + Bearer"),
            ("frag", "alt", "토큰이 유효한 경우"),
            ("msg", "JwtAuthFilter", "PolicyController", "parseUserId → SecurityContext 저장"),
            ("msg", "PolicyController", "PostgreSQL", "findRecommendCandidates(age, today)"),
            ("self", "PolicyController", "score() — 나이 · 혼인 · 지역 가중치"),
            ("ret", "PolicyController", "api/client.js", "200 { data: […] }"),
            ("else", "만료 · 위조"),
            ("ret", "JwtAuthFilter", "api/client.js", "401 TOKEN_EXPIRED"),
            ("note", "api/client.js", "status===401 && refreshToken 있음 && !config._retried 일 때만 1회"),
            ("msg", "api/client.js", "AuthService", "POST /api/auth/reissue { refreshToken }"),
            ("msg", "AuthService", "PostgreSQL", "findByToken → delete + flush → 새 토큰 INSERT"),
            ("note", "AuthService", "회전(rotation). flush 로 insert 가 delete 를 앞질러 UNIQUE 충돌 나는 것을 막는다"),
            ("ret", "AuthService", "api/client.js", "200 { accessToken, refreshToken }"),
            ("msg", "api/client.js", "JwtAuthFilter", "원래 요청 재시도 client(error.config)"),
            ("else", "재발급도 실패"),
            ("msg", "api/client.js", "사용자", "onUnauthorized() → 로그아웃 · 로그인 화면"),
            ("end",),
            ("ret", "api/client.js", "사용자", "맞춤 정책 목록 렌더링"),
        ],
    ),
    dict(
        title="정책 상세 — AI 인사이트 (캐시 · 폴백)",
        lead="AI 는 보조 기능이다. 키가 없거나 OpenAI 가 죽어도 규칙 기반으로 떨어져 본문은 항상 읽힌다.",
        src="ai/AiInsightService.java · ai/LlmInsight.java · ai/RuleBasedInsight.java · ai/AiAvailability.java",
        lanes=[("PolicyDetailView", "AiInsight.vue", "blue"), ("AiInsightController", "/api/ai", "green"),
               ("AiInsightService", "LRU 캐시 500", "violet"), ("RuleBasedInsight", "규칙 기반", "teal"),
               ("LlmInsight", "ChatClient", "rose"), ("OpenAI", "gpt-4o-mini", "slate")],
        steps=[
            ("msg", "PolicyDetailView", "AiInsightController", "GET /api/ai/policies/{policyId} + Bearer"),
            ("msg", "AiInsightController", "AiInsightService", "forPolicy(policy, user)"),
            ("msg", "AiInsightService", "RuleBasedInsight", "나이 · 마감일 · 지역을 코드로 먼저 계산"),
            ("ret", "RuleBasedInsight", "AiInsightService", "fallback (verdict · reasons · tone)"),
            ("note", "RuleBasedInsight", "틀리면 사용자가 손해 보는 판정이라 숫자와 결론은 LLM 에 맡기지 않는다"),
            ("frag", "alt", "캐시 히트 — key = policy:{policyId}:{userId}"),
            ("self", "AiInsightService", "cache.get(key) 반환 — 회원별로 답이 달라 전역 캐시는 못 쓴다"),
            ("else", "OPENAI_API_KEY 없음 · ChatModel 빈 없음"),
            ("self", "AiInsightService", "fallback 그대로 (aiGenerated=false)"),
            ("else", "LLM 호출"),
            ("msg", "AiInsightService", "LlmInsight", "forPolicy(policy, user, verdict)"),
            ("msg", "LlmInsight", "OpenAI", "SYSTEM + 확정된 판정을 담은 프롬프트"),
            ("ret", "OpenAI", "AiInsightService", "summary · reasons · nextSteps → cache.put"),
            ("note", "AiInsightService", "예외(타임아웃 · 쿼터) 는 log.warn 후 fallback — 화면을 막지 않는다"),
            ("end",),
            ("ret", "AiInsightController", "PolicyDetailView", "{ aiGenerated, summary, application }"),
            ("note", "PolicyDetailView", "aiGenerated ? 「AI」 배지 : 「샘플」 배지"),
        ],
    ),
    dict(
        title="크롤러 배치 — 실거래가 수집",
        lead="실행 한 번이 crawl_jobs 한 행. 지역 · 월 단위로 커밋해 중간에 끊겨도 앞부분은 남는다.",
        src="crawler/zipsa_crawler/{main,db}.py · transaction/{collector,client,geocode,repository}.py",
        lanes=[("운영자", "CLI · cron", "slate"), ("main.py", "argparse", "blue"),
               ("collector.py", "collect()", "violet"), ("국토부 API", "data.go.kr", "teal"),
               ("Geocoder", "카카오 로컬", "rose"), ("PostgreSQL", "crawl_jobs · transactions", "amber")],
        steps=[
            ("msg", "운영자", "main.py", "--target transaction --months 3"),
            ("msg", "main.py", "PostgreSQL", "start_job() → INSERT crawl_jobs (RUNNING)"),
            ("msg", "main.py", "collector.py", "collect(conn, settings, job_id, months)"),
            ("msg", "collector.py", "PostgreSQL", "load_regions() → 법정동코드 25개"),
            ("frag", "loop", "지역 × 최근 N개월 × (매매 · 전월세) — 서울 기준 150회"),
            ("msg", "collector.py", "국토부 API", "fetch(kind, region_code, ym)"),
            ("ret", "국토부 API", "collector.py", "Deal[] — delay_seconds 간격 준수"),
            ("msg", "collector.py", "Geocoder", "lookup(아파트명 → 좌표)"),
            ("note", "Geocoder", "이미 아는 좌표는 preload 로 걸러 다시 묻지 않는다"),
            ("msg", "collector.py", "PostgreSQL", "upsert_many() → INSERT … ON CONFLICT DO UPDATE"),
            ("msg", "collector.py", "PostgreSQL", "conn.commit() — 지역 · 월 단위"),
            ("end",),
            ("frag", "alt", "정상 종료"),
            ("msg", "main.py", "PostgreSQL", "finish_job() → status='SUCCESS'"),
            ("else", "예외 발생"),
            ("msg", "main.py", "PostgreSQL", "rollback → fail_job() → status='FAILED', error_message"),
            ("end",),
            ("ret", "main.py", "운영자", "exit code 0 / 1"),
        ],
    ),
]


# ── 렌더러 ────────────────────────────────────────────────
def render(d: dict, no: int) -> str:
    lanes = d["lanes"]
    n = len(lanes)
    span = W - 2 * M
    lw = span / n
    cx = {name: M + lw * (i + 0.5) for i, (name, _, _) in enumerate(lanes)}
    idx = {name: i for i, (name, _, _) in enumerate(lanes)}

    top_area = 78                      # 머리말이 차지하는 높이
    avail = H - top_area - 44          # 다이어그램이 쓸 수 있는 세로

    def layout(vs: float):
        """vs = 세로 여유 배율. 짧은 다이어그램은 넉넉하게 벌려 페이지를 채운다."""
        body, frags, stack = [], [], []
        y = 0.0

        def touch(*names):
            for st in stack:
                for nm in names:
                    st["lo"] = min(st["lo"], idx[nm])
                    st["hi"] = max(st["hi"], idx[nm])

        for step in d["steps"]:
            kind = step[0]

            if kind == "sec":
                y += 6 * vs
                body.append(f'<rect x="{M}" y="{y:.1f}" width="{span}" height="17" rx="4" fill="#F2F5FA"/>')
                body.append(f'<rect x="{M}" y="{y:.1f}" width="3" height="17" rx="1.5" fill="#B9C4D4"/>')
                body.append(text(M + 11, y + 12, step[1], 8.5, MUTE, weight=700, halo=False))
                y += 17 + 9 * vs

            elif kind in ("msg", "ret"):
                _, a, b, label = step
                touch(a, b)
                x1, x2 = cx[a], cx[b]
                y += ROW * vs
                dash = ' stroke-dasharray="4 3.5"' if kind == "ret" else ""
                col = RET if kind == "ret" else LINE
                head = "arrow-open" if kind == "ret" else "arrow"
                sgn = 1 if x2 > x1 else -1
                body.append(f'<line x1="{x1 + sgn * 3:.1f}" y1="{y:.1f}" x2="{x2 - sgn * 7:.1f}" '
                            f'y2="{y:.1f}" stroke="{col}" stroke-width="1.1"{dash} '
                            f'marker-end="url(#{head})"/>')
                body.append(text((x1 + x2) / 2, y - 5.5, label, 8.2,
                                 BODY if kind == "msg" else MUTE, "middle", halo=True))

            elif kind == "self":
                _, a, label = step
                touch(a)
                x = cx[a]
                y += ROW * vs
                body.append(f'<path d="M{x + 3:.1f} {y:.1f} h26 v13 h-22" fill="none" stroke="{LINE}" '
                            f'stroke-width="1.1" marker-end="url(#arrow)"/>')
                body.append(text(x + 35, y + 8, label, 8.2, BODY, halo=True))
                y += 13

            elif kind == "note":
                _, a, label = step
                touch(a)
                y += 5 * vs
                w = tw(label, 7.6) + 17
                x = min(max(cx[a] - w / 2, M + 2), W - M - w - 2)
                body.append(f'<rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="15" rx="3.5" '
                            f'fill="#FFFAEC" stroke="#EBDCB0" stroke-width="0.9"/>')
                body.append(text(x + 8.5, y + 10.5, label, 7.6, "#8A6D22"))
                y += 15

            elif kind == "frag":
                _, ftype, cond = step
                y += 9 * vs
                stack.append(dict(type=ftype, cond=cond, top=y, lo=n - 1, hi=0, elses=[]))
                y += 15 + 5 * vs

            elif kind == "else":
                f = stack[-1]
                y += 8 * vs
                f["elses"].append((y, step[1]))
                y += 11 + 4 * vs

            elif kind == "end":
                f = stack.pop()
                y += 10 * vs
                lo, hi = min(f["lo"], f["hi"]), max(f["lo"], f["hi"])
                x1 = max(M, M + lw * lo - lw * 0.32)
                x2 = min(W - M, M + lw * (hi + 1) + lw * 0.32)
                frags.append(f'<rect x="{x1:.1f}" y="{f["top"]:.1f}" width="{x2 - x1:.1f}" '
                             f'height="{y - f["top"]:.1f}" rx="6" fill="#FBFCFE" stroke="#CBD5E3" '
                             f'stroke-width="0.9"/>')
                cw = tw(f["type"], 7.5, 700) + 14
                frags.append(f'<path d="M{x1:.1f} {f["top"] + 6:.1f} a6 6 0 0 1 6 -6 h{cw:.1f} '
                             f'v14 h-{cw + 6:.1f} z" fill="#E6EBF3"/>')
                frags.append(text(x1 + 7, f["top"] + 10.5, f["type"], 7.5, "#48566B", weight=700))
                frags.append(text(x1 + cw + 13, f["top"] + 10.5, f["cond"], 7.6, MUTE, halo=True))
                for ey, elabel in f["elses"]:
                    frags.append(f'<line x1="{x1:.1f}" y1="{ey:.1f}" x2="{x2:.1f}" y2="{ey:.1f}" '
                                 f'stroke="#CBD5E3" stroke-width="0.9" stroke-dasharray="5 4"/>')
                    frags.append(text(x1 + 9, ey + 11, f"[else] {elabel}", 7.6, MUTE, halo=True))
                y += 4 * vs

        return body, frags, y

    # 1차로 재보고, 남는 공간만큼 줄 간격을 늘려 페이지를 고르게 채운다.
    _, _, raw = layout(1.0)
    room = avail - HEAD_H - 14
    vs = min(1.55, max(1.0, (room * 0.94) / raw)) if raw else 1.0
    body, frags, end_y = layout(vs)

    heads = []
    for name, sub, key in lanes:
        fill, stroke = PAL[key]
        x = cx[name]
        bw = min(lw - 10, 142)
        ty = -HEAD_H - 14
        heads.append(f'<rect x="{x - bw / 2:.1f}" y="{ty:.1f}" width="{bw:.1f}" height="{HEAD_H}" '
                     f'rx="7" fill="{fill}" stroke="{stroke}" stroke-width="1.05"/>')
        if sub:
            heads.append(text(x, ty + 15, name, 9.2, INK, "middle", weight=700))
            heads.append(text(x, ty + 26, sub, 7.3, stroke, "middle"))
        else:
            heads.append(text(x, ty + 21, name, 9.4, INK, "middle", weight=700))
        heads.append(f'<line x1="{x:.1f}" y1="-9" x2="{x:.1f}" y2="{end_y + 12:.1f}" '
                     f'stroke="#B9C4D4" stroke-width="1" stroke-dasharray="2.5 4.5" opacity="0.8"/>')

    used = end_y + HEAD_H + 14
    dy = top_area + HEAD_H + 14 + max(0.0, (avail - used) / 2)

    out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" '
           f'font-family="{FONT}">',
           f'<rect width="{W}" height="{H}" fill="#FFFFFF"/>',
           '<defs><!--GLYPHS-->',
           f'<marker id="arrow" viewBox="0 0 10 10" refX="8.5" refY="5" markerWidth="6.5" '
           f'markerHeight="6.5" orient="auto-start-reverse"><path d="M0 1 L9 5 L0 9 z" '
           f'fill="{LINE}"/></marker>',
           f'<marker id="arrow-open" viewBox="0 0 10 10" refX="8.5" refY="5" markerWidth="7" '
           f'markerHeight="7" orient="auto-start-reverse"><path d="M0.8 1.2 L8.8 5 L0.8 8.8" '
           f'fill="none" stroke="{RET}" stroke-width="1.5" stroke-linecap="round" '
           f'stroke-linejoin="round"/></marker>',
           '</defs>']

    out.append(f'<rect x="{M}" y="28" width="3.5" height="31" rx="1.75" fill="{INK}"/>')
    out.append(text(M + 13, 34, f"SEQUENCE {no}", 7.6, MUTE, weight=700))
    out.append(text(M + 13, 51, d["title"], 16, INK, weight=700))
    out.append(text(M + 13, 69, d["lead"], 8.6, BODY))
    out.append(text(W - M, 69, d["src"], 7.3, "#A3AEBD", "end"))
    out.append(f'<line x1="{M}" y1="{H - 27}" x2="{W - M}" y2="{H - 27}" stroke="#E7ECF3" '
               f'stroke-width="1"/>')
    out.append(text(M, H - 15, "ZIP 보금자리 · ZipSa — 시퀀스 다이어그램", 7.4, "#A3AEBD"))
    out.append(text(W - M, H - 15, f"{no} / {len(DIAGRAMS)}", 7.4, "#A3AEBD", "end"))

    out.append(f'<g transform="translate(0 {dy:.1f})">')
    out += frags + heads + body
    out.append('</g></svg>')
    return "\n".join(out)


def cover() -> str:
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" '
         f'font-family="{FONT}">',
         f'<rect width="{W}" height="{H}" fill="#FFFFFF"/>',
         '<defs><!--GLYPHS--></defs>',
         f'<rect x="0" y="0" width="{W}" height="7" fill="{INK}"/>']
    o.append(text(M + 8, 152, "ZIP 보금자리 · ZipSa", 11, MUTE, weight=700))
    o.append(text(M + 8, 214, "시퀀스 다이어그램", 46, INK, weight=700))
    o.append(text(M + 8, 246, "청년 주거 정책 · 공공임대 · 실거래가 통합 플랫폼", 12, BODY))
    o.append(text(M + 8, 266, "Vue 3 SPA → Spring Boot 3.5 (Spring AI) → PostgreSQL 17 ← Python 크롤러", 12, BODY))
    y = 330
    o.append(f'<line x1="{M + 8}" y1="{y - 22}" x2="{W - M - 8}" y2="{y - 22}" stroke="#D9E0EA" stroke-width="1"/>')
    for i, d in enumerate(DIAGRAMS):
        o.append(text(M + 8, y, str(i + 1), 12, "#A3AEBD", weight=700))
        o.append(text(M + 34, y, d["title"], 12.5, INK, weight=700))
        o.append(text(M + 330, y, d["lead"], 9.5, BODY))
        o.append(f'<line x1="{M + 8}" y1="{y + 13}" x2="{W - M - 8}" y2="{y + 13}" '
                 f'stroke="#EDF1F7" stroke-width="1"/>')
        y += 42
    o.append(text(M + 8, H - 74, "github.com/skeey66/ZipSa", 9, MUTE))
    o.append(text(M + 8, H - 58, "실제 코드(Flyway V1~V21 적용 스키마)에서 그린 문서입니다. "
                                 "scripts/build_sequence_pdf.py 로 다시 생성합니다.", 9, MUTE))
    o.append('</svg>')
    return "\n".join(o)


def build(outline: bool) -> list[str]:
    """outline=True 면 글자를 패스로 바꾼 사본을 만든다(PDF 용)."""
    global OUTLINE
    OUTLINE = outline
    pages = []
    for maker in [cover] + [(lambda d=d, i=i: render(d, i + 1)) for i, d in enumerate(DIAGRAMS)]:
        GL.defs.clear()
        GL._cache.clear()
        svg = maker()
        if outline:
            defs = "".join(f'<path id="{gid}" d="{d}"/>' for gid, d in GL.defs.items())
            svg = svg.replace("<!--GLYPHS-->", defs, 1)
        else:
            svg = svg.replace("<!--GLYPHS-->", "", 1)
        pages.append(svg)
    OUTLINE = False
    return pages


def main() -> int:
    DIA.mkdir(parents=True, exist_ok=True)

    names = ["seq-0-overview", "seq-1-auth", "seq-2-token-reissue", "seq-3-ai-insight", "seq-4-crawler"]
    for name, svg in zip(names, build(outline=False)[1:]):
        (DIA / f"{name}.svg").write_text(svg, encoding="utf-8")

    body = "".join(f'<section class="pg">{p}</section>' for p in build(outline=True))
    html = f"""<!doctype html><html lang="ko"><head><meta charset="utf-8">
<title>ZipSa 시퀀스 다이어그램</title><style>
@page {{ size: {W}px {H}px; margin: 0; }}
html,body {{ margin:0; padding:0; background:#fff; }}
.pg {{ width:{W}px; height:{H}px; overflow:hidden; break-after:page; }}
.pg:last-child {{ break-after:auto; }}
</style></head><body>{body}</body></html>"""

    tmp = pathlib.Path(tempfile.gettempdir()) / "zipsa-sequence.html"
    tmp.write_text(html, encoding="utf-8")

    chrome = CHROME if pathlib.Path(CHROME).exists() else (
        shutil.which("google-chrome") or shutil.which("chromium"))
    if not chrome:
        print(f"Chrome 을 찾지 못했습니다. HTML 만 만들었습니다: {tmp}")
        return 1
    subprocess.run([chrome, "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
                    f"--print-to-pdf={PDF}", "--virtual-time-budget=8000", tmp.as_uri()],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print(f"{PDF.relative_to(ROOT)} — {len(DIAGRAMS) + 1}쪽, {PDF.stat().st_size // 1024}KB "
          f"(A4 가로 {W}×{H}px, 글자는 아웃라인)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
