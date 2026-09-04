# -*- coding: utf-8 -*-
"""docs/architecture.md → docs/ZipSa-시스템아키텍처.pdf

    python3 scripts/build_architecture_pdf.py

architecture.md 안의 mermaid 블록과 표를 그대로 읽어 A4 가로 PDF 로 찍습니다.
**문서가 원본이고 PDF 는 산출물입니다.** 내용을 고칠 때는 PDF 가 아니라 md 를 고치고
이 스크립트를 다시 돌리세요.

페이지로 뽑을 블록은 md 안에 주석으로 표시합니다(md 로 볼 때는 보이지 않습니다).

    <!-- pdf       title="..." desc="..." src="..." -->   바로 뒤 ```mermaid``` 를 그림 페이지로
    <!-- pdf-block title="..." desc="..." src="..." -->   바로 뒤 표·코드·문단을 글 페이지로

의존:
  · mermaid — scripts/.mermaid 에 npm 으로 한 번 받아 두고 재사용합니다(최초 1회 네트워크 필요).
  · fontTools — 없으면 폰트 임베딩을 건너뜁니다(한글이 깨질 수 있음).

폰트를 왜 직접 넣나:
  build_sequence_pdf.py 와 같은 이유입니다. 헤드리스 Chrome 은 Apple SD Gothic Neo(.ttc) 를
  Type 3 글리프로 박아버려서 뷰어에 따라 한글이 깨집니다. 필요한 글자만 서브셋한 OTF 를
  base64 로 @font-face 에 심으면 정상적인 CID 폰트로 임베드됩니다.
"""
from __future__ import annotations

import base64
import datetime
import html
import io
import json
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "docs/architecture.md"
PDF = ROOT / "docs/ZipSa-시스템아키텍처.pdf"
HTML_OUT = pathlib.Path(tempfile.gettempdir()) / "zipsa-architecture.html"
MERMAID_DIR = ROOT / "scripts/.mermaid"
MERMAID_JS = MERMAID_DIR / "node_modules/mermaid/dist/mermaid.min.js"
TTC = "/System/Library/Fonts/AppleSDGothicNeo.ttc"
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

# A4 가로 @96dpi. build_sequence_pdf.py 와 같은 눈금이라 두 PDF 를 나란히 봐도 어긋나지 않습니다.
W, H = 1123, 794
PAD = 34
CAP_H = 92                  # 캡션(제목 + 한 줄 설명 + 출처) 높이

INK, BODY, MUTE = "#111927", "#33415A", "#7C899B"
RULE, FAINT = "#D7DEE8", "#EDF1F7"


# ── md 파싱 ────────────────────────────────────────────────
DIRECTIVE = re.compile(r"<!--\s*(pdf|pdf-block)\s+(.*?)-->", re.S)
ATTR = re.compile(r'(\w+)="(.*?)"', re.S)


def parse(md: str) -> list[dict]:
    """지시 주석과 그 뒤에 오는 블록을 페이지 목록으로 만든다."""
    pages, marks = [], list(DIRECTIVE.finditer(md))
    for i, m in enumerate(marks):
        attrs = dict(ATTR.findall(m.group(2)))
        # 다음 지시 주석 전까지가 이 페이지의 몫이다.
        end = marks[i + 1].start() if i + 1 < len(marks) else len(md)
        body = md[m.end():end]
        if m.group(1) == "pdf":
            fence = re.search(r"```mermaid\n(.*?)```", body, re.S)
            if not fence:
                sys.exit(f"'{attrs.get('title')}' 뒤에 mermaid 블록이 없습니다.")
            pages.append({"kind": "mermaid", "code": fence.group(1).strip(), **attrs})
        else:
            pages.append({"kind": "block", "body": block_html(body), **attrs})
    return pages


# ── 최소 마크다운 렌더러 ────────────────────────────────────
# 표 · 코드펜스 · 목록 · 인용 · 문단만 다룹니다. 이 문서가 쓰는 문법이 그게 전부입니다.
INLINE = [
    (re.compile(r"`([^`]+)`"), r"<code>\1</code>"),
    (re.compile(r"\*\*([^*]+)\*\*"), r"<strong>\1</strong>"),
]


def inline(s: str) -> str:
    s = html.escape(s)
    for pat, rep in INLINE:
        s = pat.sub(rep, s)
    return s.replace("&lt;br&gt;", "<br>")


def block_html(md: str) -> str:
    out, lines, i = [], md.strip("\n").split("\n"), 0
    while i < len(lines):
        line = lines[i]

        if line.startswith("```"):                       # 코드 펜스
            lang = line[3:].strip()
            i += 1
            buf = []
            while i < len(lines) and not lines[i].startswith("```"):
                buf.append(lines[i])
                i += 1
            i += 1
            out.append(f'<pre class="code {html.escape(lang)}">'
                       f'{html.escape(chr(10).join(buf))}</pre>')
            continue

        if line.startswith("|"):                          # 표
            rows = []
            while i < len(lines) and lines[i].startswith("|"):
                rows.append([c.strip() for c in lines[i].strip("|").split("|")])
                i += 1
            if len(rows) >= 2 and set("-: ") >= set("".join(rows[1])):
                head, body_rows = rows[0], rows[2:]
            else:
                head, body_rows = None, rows
            t = ['<table class="grid">']
            if head:
                t.append("<thead><tr>" + "".join(f"<th>{inline(c)}</th>" for c in head)
                         + "</tr></thead>")
            t.append("<tbody>" + "".join(
                "<tr>" + "".join(f"<td>{inline(c)}</td>" for c in r) + "</tr>"
                for r in body_rows) + "</tbody></table>")
            out.append("".join(t))
            continue

        if line.startswith("- "):                          # 목록
            items = []
            while i < len(lines) and lines[i].startswith("- "):
                items.append(inline(lines[i][2:]))
                i += 1
            out.append("<ul>" + "".join(f"<li>{x}</li>" for x in items) + "</ul>")
            continue

        if line.startswith(">"):                           # 인용
            buf = []
            while i < len(lines) and lines[i].startswith(">"):
                buf.append(lines[i].lstrip("> ").rstrip())
                i += 1
            out.append(f'<blockquote>{inline(" ".join(buf))}</blockquote>')
            continue

        if line.strip() in ("", "---") or line.startswith("#"):
            i += 1
            continue

        # 문단. 첫 줄은 조건 없이 삼킨다 — 여기까지 왔다는 건 위 분기가 모두 아니라는 뜻이고,
        # 조건부로만 먹으면 `code` 로 시작하는 줄에서 i 가 전진하지 않아 무한루프가 된다.
        buf = [line.strip()]
        i += 1
        while i < len(lines) and lines[i].strip() and lines[i][:1] not in "|>#" \
                and not lines[i].startswith("- ") and not lines[i].startswith("```"):
            buf.append(lines[i].strip())
            i += 1
        out.append(f"<p>{inline(' '.join(buf))}</p>")
    return "".join(out)


# ── mermaid 준비 ───────────────────────────────────────────
def ensure_mermaid() -> str:
    if MERMAID_JS.exists():
        return MERMAID_JS.read_text(encoding="utf-8")
    if not shutil.which("npm"):
        sys.exit("npm 이 없어 mermaid 를 받을 수 없습니다. Node 를 설치하고 다시 돌리세요.")
    print("  mermaid 를 scripts/.mermaid 에 받는 중… (최초 1회)")
    MERMAID_DIR.mkdir(parents=True, exist_ok=True)
    # package.json 이 없으면 npm 이 상위 디렉터리를 제 프로젝트로 착각해
    # "up to date" 만 찍고 아무것도 설치하지 않는다.
    (MERMAID_DIR / "package.json").write_text(
        '{"name":"zipsa-pdf-mermaid","private":true,"version":"0.0.0"}\n', encoding="utf-8")
    subprocess.run(["npm", "install", "--silent", "--no-audit", "--no-fund", "mermaid@11"],
                   cwd=MERMAID_DIR, check=True,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=600)
    if not MERMAID_JS.exists():
        sys.exit(f"mermaid 설치에 실패했습니다: {MERMAID_DIR}")
    return MERMAID_JS.read_text(encoding="utf-8")


# ── 폰트 서브셋 ────────────────────────────────────────────
def subset_font(face: int, chars: str) -> str | None:
    try:
        from fontTools import subset
        from fontTools.ttLib import TTCollection
    except ImportError:
        return None
    font = TTCollection(TTC).fonts[face]
    opts = subset.Options(layout_features=[], notdef_outline=True, desubroutinize=True)
    opts.drop_tables += ["BASE", "VORG", "vhea", "vmtx", "meta"]
    sub = subset.Subsetter(options=opts)
    sub.populate(text=chars)
    sub.subset(font)
    buf = io.BytesIO()
    font.save(buf)
    return base64.b64encode(buf.getvalue()).decode()


def font_css(chars: str) -> str:
    if not pathlib.Path(TTC).exists():
        return ""
    reg, bold = subset_font(0, chars), subset_font(6, chars)
    if not reg or not bold:
        print("  fontTools 가 없어 폰트 임베딩을 건너뜁니다(뷰어에 따라 한글이 깨질 수 있음).")
        return ""
    return (f"@font-face {{ font-family:'ZipSaKR'; font-weight:400; font-style:normal;"
            f" src:url(data:font/otf;base64,{reg}) format('opentype'); }}\n"
            f"@font-face {{ font-family:'ZipSaKR'; font-weight:700; font-style:normal;"
            f" src:url(data:font/otf;base64,{bold}) format('opentype'); }}")


# ── HTML 조립 ──────────────────────────────────────────────
CSS = f"""
@page {{ size: {W}px {H}px; margin: 0; }}
* {{ box-sizing: border-box; }}
html, body {{ margin:0; padding:0; background:#fff; }}
body {{ font-family:'ZipSaKR','Apple SD Gothic Neo','Noto Sans KR',sans-serif;
        color:{INK}; -webkit-print-color-adjust:exact; print-color-adjust:exact; }}
.pg {{ width:{W}px; height:{H}px; padding:{PAD}px; overflow:hidden; position:relative;
       break-after:page; display:flex; flex-direction:column; }}
.pgno {{ position:absolute; left:{PAD}px; right:{PAD}px; bottom:14px;
         display:flex; justify-content:space-between;
         font-size:9px; color:{MUTE}; font-family:ui-monospace,Menlo,monospace; }}
.pg:last-child {{ break-after:auto; }}

/* 표지 */
.cover {{ justify-content:center; }}
.cover .inner {{ border-top:5px solid {INK}; padding-top:30px; }}
.kicker {{ margin:0 0 8px; font-size:13px; letter-spacing:.22em; color:{MUTE}; font-weight:700; }}
h1 {{ margin:0; font-size:56px; line-height:1.06; letter-spacing:-.02em; }}
.sub {{ margin:16px 0 0; font-size:15px; line-height:1.7; color:{BODY}; }}
.toc {{ margin:26px 0 0; padding:0; list-style:none; border-top:1px solid {RULE}; }}
.toc li {{ display:grid; grid-template-columns:30px 250px 1fr; gap:14px; align-items:baseline;
           padding:8px 2px; border-bottom:1px solid {FAINT}; }}
.toc .n {{ font-size:12px; font-weight:700; color:{MUTE}; font-family:ui-monospace,Menlo,monospace; }}
.toc .t {{ font-size:14px; font-weight:700; }}
.toc .d {{ font-size:11.5px; color:{MUTE}; line-height:1.45; }}
.meta {{ margin:22px 0 0; font-size:11px; line-height:1.6; color:{MUTE};
         font-family:ui-monospace,Menlo,monospace; }}

/* 본문 페이지 머리 */
.cap {{ height:{CAP_H}px; border-left:4px solid {INK}; padding-left:14px; flex:none; }}
.no {{ margin:0 0 4px; font-size:10px; letter-spacing:.18em; color:{MUTE}; font-weight:700;
       font-family:ui-monospace,Menlo,monospace; }}
.cap h2 {{ margin:0; font-size:22px; letter-spacing:-.01em; }}
.cap .desc {{ margin:6px 0 0; font-size:12px; line-height:1.5; color:{BODY}; }}
.cap .src {{ margin:5px 0 0; font-size:9.5px; color:{MUTE};
             font-family:ui-monospace,Menlo,monospace; }}

/* 그림 */
.frame {{ flex:1; display:flex; align-items:center; justify-content:center; min-height:0; }}
.frame svg {{ display:block; }}

/* 글 페이지 */
.body {{ flex:1; min-height:0; overflow:hidden; padding-top:10px; font-size:11.5px; line-height:1.62;
         color:{BODY}; column-fill:auto; }}
.fit {{ transform-origin: top left; }}
.body p {{ margin:0 0 8px; }}
.body ul {{ margin:0 0 8px; padding-left:16px; }}
.body li {{ margin:0 0 4px; }}
.body strong {{ color:{INK}; }}
.body code {{ font-family:ui-monospace,Menlo,monospace; font-size:10.5px;
              background:{FAINT}; padding:1px 4px; border-radius:3px; color:#1F3350; }}
.body blockquote {{ margin:8px 0; padding:8px 12px; border-left:3px solid #EF6C00;
                    background:#FFF8F0; font-size:11px; color:{BODY}; }}
pre.code {{ font-family:ui-monospace,Menlo,monospace; font-size:9.1px; line-height:1.5;
            background:#F7F9FC; border:1px solid {RULE}; border-radius:6px;
            padding:10px 12px; margin:0 0 8px; white-space:pre; overflow:hidden; color:#1F3350; }}
pre.code.text {{ font-size:8.6px; line-height:1.46; }}
table.grid {{ width:100%; border-collapse:collapse; margin:0 0 10px; font-size:10.5px; }}
table.grid th {{ background:{FAINT}; text-align:left; font-weight:700; color:{INK};
                 padding:5px 8px; border:1px solid {RULE}; }}
table.grid td {{ padding:5px 8px; border:1px solid {RULE}; vertical-align:top; }}
"""

MERMAID_THEME = {
    "primaryColor": "#F2F6FC", "primaryTextColor": INK, "primaryBorderColor": "#93A4BC",
    "lineColor": "#5B6B80", "secondaryColor": "#EDF1F7", "tertiaryColor": "#F7F9FC",
    "clusterBkg": "#FBFCFE", "clusterBorder": "#C9D4E2",
    "fontFamily": "'ZipSaKR','Apple SD Gothic Neo',sans-serif", "fontSize": "15px",
    # 시퀀스
    "actorBkg": "#EDF1F7", "actorBorder": "#5B6B80", "actorTextColor": INK,
    "signalColor": "#33415A", "signalTextColor": "#33415A",
    "labelBoxBkg": "#E8EDF6", "labelBoxBorderColor": "#5B6B80", "noteBkgColor": "#FFF8E1",
    "noteBorderColor": "#C8A44A", "noteTextColor": "#5C4708",
    # ER
    "attributeBackgroundColorOdd": "#FFFFFF", "attributeBackgroundColorEven": "#F5F8FC",
}

# 렌더 후 각 SVG 를 제 프레임에 맞춰 넣는다. mermaid 는 제 마음대로 크기를 잡으므로
# 여기서 다시 재본다. 1.0 을 넘겨 키우지 않는다 — 선이 뭉개진다.
#
# 글 페이지도 같은 방식으로 재본다. 내용이 한 쪽을 넘으면 잘라내지 말고 통째로 줄인다.
# (문서를 고칠 때마다 페이지 넘침을 손으로 맞추지 않아도 되게 하려는 것이다)
FIT_JS = """
document.querySelectorAll('.frame').forEach(f => {
  const svg = f.querySelector('svg');
  if (!svg) return;
  const vb = svg.viewBox.baseVal;
  const w = vb && vb.width ? vb.width : svg.getBoundingClientRect().width;
  const h = vb && vb.height ? vb.height : svg.getBoundingClientRect().height;
  const s = Math.min(f.clientWidth / w, f.clientHeight / h, 1);
  svg.removeAttribute('style');
  svg.setAttribute('width', Math.floor(w * s));
  svg.setAttribute('height', Math.floor(h * s));
});
document.querySelectorAll('.body > .fit').forEach(f => {
  f.style.transform = 'none';
  f.style.width = '100%';
  const have = f.parentElement.clientHeight;
  const need = f.scrollHeight;
  if (need <= have) return;
  const s = Math.max(have / need, 0.5);
  f.style.transform = 'scale(' + s + ')';
  f.style.width = (100 / s) + '%';
  f.dataset.scaled = s.toFixed(3);
});
"""


def cover(pages: list[dict]) -> str:
    today = datetime.date.today().isoformat()
    items = "".join(
        f'<li><span class="n">{i + 1:02d}</span>'
        f'<span class="t">{html.escape(p.get("title", ""))}</span>'
        f'<span class="d">{html.escape(p.get("desc", ""))}</span></li>'
        for i, p in enumerate(pages))
    return f"""<section class="pg cover"><div class="inner">
  <p class="kicker">ZIP 보금자리 · ZipSa</p>
  <h1>시스템 아키텍처</h1>
  <p class="sub">청년 주거 정책 · 공공임대 · 실거래가 통합 플랫폼<br>
     Vue 3.5 SPA → Spring Boot 3.5 (Spring AI) → PostgreSQL 17 ← Python 크롤러 배치</p>
  <ol class="toc">{items}</ol>
  <p class="meta">github.com/skeey66/ZipSa · {today}<br>
     docs/architecture.md 에서 생성 — 코드(Flyway V1~V21)를 대조해 그린 문서입니다.</p>
</div></section>"""


def build() -> tuple[str, int]:
    md = SRC.read_text(encoding="utf-8")
    pages = parse(md)
    if not pages:
        sys.exit("architecture.md 에 <!-- pdf ... --> 지시가 없습니다.")

    body = [cover(pages)]
    for i, p in enumerate(pages):
        cap = (f'<header class="cap"><p class="no">{i + 1:02d}</p>'
               f'<h2>{html.escape(p.get("title", ""))}</h2>'
               f'<p class="desc">{html.escape(p.get("desc", ""))}</p>'
               f'<p class="src">{html.escape(p.get("src", ""))}</p></header>')
        if p["kind"] == "mermaid":
            inner = f'<div class="frame"><pre class="mermaid">{html.escape(p["code"])}</pre></div>'
        else:
            inner = f'<div class="body"><div class="fit">{p["body"]}</div></div>'
        foot = (f'<div class="pgno"><span>ZipSa 시스템 아키텍처</span>'
                f'<span>{i + 2} / {len(pages) + 1}</span></div>')
        body.append(f'<section class="pg">{cap}{inner}{foot}</section>')

    html_body = "".join(body)
    # 서브셋에 넣을 글자 — 다이어그램 안 글자까지 포함해야 한다.
    chars = "".join(sorted(set(html_body + md))) + "0123456789"

    doc = f"""<!doctype html><html lang="ko"><head><meta charset="utf-8">
<title>ZipSa 시스템 아키텍처</title>
<style>{font_css(chars)}
{CSS}</style></head>
<body>{html_body}
<script>{ensure_mermaid()}</script>
<script>
mermaid.initialize({{ startOnLoad:false, theme:'base', securityLevel:'loose',
  themeVariables:{json.dumps(MERMAID_THEME, ensure_ascii=False)},
  flowchart:{{ htmlLabels:false, curve:'basis', nodeSpacing:34, rankSpacing:44 }},
  sequence:{{ useMaxWidth:false, actorFontSize:13, noteFontSize:12, messageFontSize:12 }},
  er:{{ useMaxWidth:false, fontSize:12 }} }});
window.__fit = function () {{
  {FIT_JS}
  return document.querySelectorAll('[data-scaled]').length;
}};
mermaid.run({{ querySelector:'.mermaid' }}).then(() => {{
  document.body.dataset.ready = '1';
}}).catch(e => {{ document.body.dataset.error = String(e); }});
</script></body></html>"""
    return doc, len(pages) + 1


# ── 인쇄 ───────────────────────────────────────────────────
# Chrome CLI(--print-to-pdf)는 mermaid 렌더가 끝나기를 기다려 주지 않아,
# 페이지에 맞춰 줄이는 스크립트가 반영되기 전에 인쇄돼 내용이 잘렸습니다.
# puppeteer 로 body[data-ready] 를 기다린 뒤 인쇄합니다. 쪽번호 꼬리말은 덤입니다.
NODE_SCRIPT = """
const puppeteer = require('puppeteer-core');
(async () => {
  const [src, out, chrome, footer, w, h, pages] = process.argv.slice(2);
  const browser = await puppeteer.launch({ executablePath: chrome, headless: 'new',
    args: ['--no-sandbox', '--font-render-hinting=none'] });
  const page = await browser.newPage();
  await page.setViewport({ width: Number(w), height: Number(h) });
  await page.goto('file://' + src, { waitUntil: 'networkidle0', timeout: 120000 });
  await page.waitForSelector('body[data-ready="1"]', { timeout: 120000 });
  // 인쇄와 같은 레이아웃에서 재야 한다. 화면 기준으로 재면 값이 어긋나 잘린다.
  await page.emulateMediaType('print');
  const scaled = await page.evaluate(() => window.__fit());
  console.error('  페이지에 맞춰 줄인 글 블록: ' + scaled + '개');
  await page.pdf({ path: out, width: w + 'px', height: h + 'px', printBackground: true,
    displayHeaderFooter: false,
    margin: { top: '0', right: '0', bottom: '0', left: '0' }, pageRanges: '1-' + pages });
  await browser.close();
})();
"""

FOOTER = (
    '<div style="width:100%;font-size:7pt;color:#8A949E;padding:0 34px;margin-top:-24px;'
    'font-family:-apple-system,sans-serif;display:flex;justify-content:space-between;">'
    '<span>ZipSa 시스템 아키텍처</span>'
    '<span><span class="pageNumber"></span> / <span class="totalPages"></span></span></div>'
)

NODE_CACHE = ROOT / "scripts/.pdf-node"


def print_with_puppeteer(chrome: str, n: int) -> bool:
    if not shutil.which("npm"):
        return False
    try:
        if not (NODE_CACHE / "node_modules/puppeteer-core").exists():
            NODE_CACHE.mkdir(parents=True, exist_ok=True)
            (NODE_CACHE / "package.json").write_text(
                '{"name":"zipsa-pdf-node","private":true,"version":"0.0.0"}\n', encoding="utf-8")
            subprocess.run(["npm", "install", "--silent", "--no-audit", "--no-fund",
                            "puppeteer-core@23"], cwd=NODE_CACHE, check=True,
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=600)
        script = NODE_CACHE / "topdf-architecture.js"
        script.write_text(NODE_SCRIPT, encoding="utf-8")
        subprocess.run(["node", str(script), str(HTML_OUT), str(PDF), chrome,
                        FOOTER, str(W), str(H), str(n)],
                       check=True, timeout=600)
        return True
    except Exception as e:                       # noqa: BLE001 — 실패하면 CLI 로 떨어진다
        print(f"  puppeteer 사용 불가({e}) — Chrome CLI 로 대체합니다(내용이 잘릴 수 있음).")
        return False


def main() -> int:
    doc, n = build()
    HTML_OUT.write_text(doc, encoding="utf-8")

    chrome = CHROME if pathlib.Path(CHROME).exists() else (
        shutil.which("google-chrome") or shutil.which("chromium"))
    if not chrome:
        print(f"Chrome 을 찾지 못했습니다. HTML 만 만들었습니다: {HTML_OUT}")
        return 1

    if not print_with_puppeteer(chrome, n):
        subprocess.run([chrome, "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
                        f"--print-to-pdf={PDF}", "--virtual-time-budget=30000",
                        HTML_OUT.as_uri()],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print(f"{PDF.relative_to(ROOT)} — {n}쪽, {PDF.stat().st_size // 1024}KB "
          f"(A4 가로 {W}×{H}px)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
