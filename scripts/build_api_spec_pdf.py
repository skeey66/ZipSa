# -*- coding: utf-8 -*-
"""docs/api/API.yml → docs/api/ZipSa-API-명세서.pdf

    python3 scripts/build_api_spec_pdf.py

API.yml 을 읽어 '인쇄용' 명세서 HTML 을 만들고 헤드리스 Chrome 으로 PDF 를 찍습니다.
Swagger UI 를 그대로 인쇄하지 않는 이유: 펼침 상태에 따라 내용이 잘리고, 종이에서 읽기 좋은
요청·응답 '항목표' 가 없습니다. 그래서 오퍼레이션마다 파라미터·본문·응답·예외를 표로 다시 폅니다.

명세를 고칠 때는 이 PDF 가 아니라 **API.yml 을 고치고 이 스크립트를 다시 돌리세요.**

페이지 번호가 있는 꼬리말은 puppeteer-core 가 있을 때만 붙습니다(Chrome CLI 는 꼬리말에
file:// 경로를 박아넣기 때문에 씁니다). npm 이 없으면 꼬리말 없이 그대로 생성됩니다.
"""
import datetime
import html
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import yaml

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / 'docs/api/API.yml'
PDF = ROOT / 'docs/api/ZipSa-API-명세서.pdf'
HTML_OUT = pathlib.Path(tempfile.gettempdir()) / 'zipsa-api-spec.html'
NODE_CACHE = ROOT / 'scripts/.pdf-node'
CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'

DOC = yaml.safe_load(SRC.read_text(encoding='utf-8'))

SCHEMAS = DOC['components']['schemas']
RESPONSES = DOC['components']['responses']
PARAMS = DOC['components']['parameters']

METHODS = ('get', 'post', 'patch', 'put', 'delete')


# ── $ref / allOf 해석 ────────────────────────────────────────
def deref(node):
    """$ref 를 따라간다. (스키마, 참조된 이름) 을 돌려준다."""
    name = None
    seen = 0
    while isinstance(node, dict) and '$ref' in node:
        ref = node['$ref']
        section, key = ref.split('/')[-2], ref.split('/')[-1]
        name = key
        node = {'schemas': SCHEMAS, 'responses': RESPONSES, 'parameters': PARAMS}[section][key]
        seen += 1
        if seen > 10:
            break
    return node, name


def merge(schema):
    """allOf 를 하나로 합친다. 뒤에 오는 조각이 앞을 덮어쓴다(우리 문서의 Envelope 규칙)."""
    schema, name = deref(schema)
    if not isinstance(schema, dict):
        return {}, name
    if 'allOf' not in schema:
        return schema, name
    out, ref_name = {}, name
    for part in schema['allOf']:
        merged, pname = merge(part)
        ref_name = ref_name or pname
        for k, v in merged.items():
            if k == 'properties':
                out.setdefault('properties', {})
                out['properties'].update(v)
            elif k == 'required':
                out['required'] = sorted(set(out.get('required', [])) | set(v))
            else:
                out[k] = v
    for k, v in schema.items():
        if k != 'allOf':
            if k == 'properties':
                out.setdefault('properties', {}).update(v)
            else:
                out[k] = v
    return out, ref_name


def type_label(schema):
    s, name = merge(schema)
    t = s.get('type')
    if 'enum' in s:
        vals = [str(v) for v in s['enum'] if v is not None]
        label = 'enum'
        if name:
            label = f'enum({name})'
        return f"{label}<br><span class='enumvals'>{' · '.join(vals)}</span>"
    if t == 'array':
        inner, iname = merge(s.get('items', {}))
        it = iname or inner.get('type', 'object')
        if 'enum' in inner:
            it = iname or 'enum'
        return f'array&lt;{it}&gt;'
    if t == 'integer':
        return f"integer({s.get('format', 'int32')})"
    if t == 'number':
        return f"number({s.get('format', 'double')})"
    if t == 'string':
        if name in ('LocalDateTime',):
            return 'string(date-time)'
        if s.get('format'):
            return f"string({s['format']})"
        return 'string'
    if t == 'object' or 'properties' in s:
        return name or 'object'
    if t:
        return t
    return name or 'any'


def constraints(s):
    bits = []
    for key, fmt in (('minLength', '최소 {}자'), ('maxLength', '최대 {}자'),
                     ('minimum', '최솟값 {}'), ('maximum', '최댓값 {}'),
                     ('minItems', '최소 {}개')):
        if key in s:
            bits.append(fmt.format(s[key]))
    if 'pattern' in s:
        bits.append(f"형식 <code>{html.escape(s['pattern'])}</code>")
    if 'default' in s:
        bits.append(f"기본값 <code>{json.dumps(s['default'], ensure_ascii=False)}</code>")
    return bits


MAX_DEPTH = 3

SHORT_DESC = {
    'LocalDateTime': 'ISO-8601 (`yyyy-MM-ddTHH:mm:ss`, 오프셋 없음)',
}


def brief(text, limit=150):
    """필드표에 넣을 한 줄 설명. 첫 문장만 남긴다."""
    line = next((l.strip() for l in text.split('\n') if l.strip()), '')
    if len(line) > limit:
        cut = line[:limit]
        line = cut[:cut.rfind(' ')] if ' ' in cut else cut
        line += '…'
    return line


def flatten(schema, prefix='', depth=0, rows=None, required_here=None):
    """중첩 객체를 점 표기(`a.b`)·배열 표기(`a[]`)로 편다."""
    if rows is None:
        rows = []
    s, _ = merge(schema)
    props = s.get('properties') or {}
    req = set(s.get('required') or [])
    for key, raw in props.items():
        child, cname = merge(raw)
        name = f'{prefix}{key}'
        desc_bits = []
        if cname in SHORT_DESC:
            rows.append({'name': name, 'type': type_label(raw),
                         'required': 'Y' if key in req else '',
                         'desc': md_inline(SHORT_DESC[cname])})
            continue
        if child.get('description'):
            desc_bits.append(md_inline(brief(child['description'])))
        desc_bits += [c for c in constraints(child) if len(c) < 90]
        if child.get('nullable'):
            desc_bits.append('null 가능')
        ex = child.get('example', raw.get('example'))
        if ex is not None and not isinstance(ex, (dict, list)):
            desc_bits.append(f'예) <code>{html.escape(str(ex))}</code>')
        rows.append({
            'name': name,
            'type': type_label(raw),
            'required': 'Y' if key in req else '',
            'desc': ' · '.join(b for b in desc_bits if b),
        })
        if depth < MAX_DEPTH:
            if child.get('type') == 'array':
                item, _ = merge(child.get('items', {}))
                if item.get('properties'):
                    flatten(child['items'], f'{name}[].', depth + 1, rows)
            elif child.get('properties'):
                flatten(raw, f'{name}.', depth + 1, rows)
    return rows


# ── 아주 작은 마크다운 렌더러 (설명·예외 표에 쓰는 문법만) ──
def md_inline(text):
    t = html.escape(text)
    t = re.sub(r'`([^`]+)`', r'<code>\1</code>', t)
    t = re.sub(r'\*\*([^*]+)\*\*', r'<strong>\1</strong>', t)
    t = re.sub(r'\[([^\]]+)\]\(([^)]+)\)', r'<a href="\2">\1</a>', t)
    t = t.replace('\n', ' ')
    return t


def md_block(text):
    if not text:
        return ''
    out, buf, lines = [], [], text.split('\n')
    i = 0

    def flush():
        if buf:
            out.append('<p>' + md_inline(' '.join(buf)) + '</p>')
            buf.clear()

    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        if stripped.startswith('```'):
            flush()
            i += 1
            code = []
            while i < len(lines) and not lines[i].strip().startswith('```'):
                code.append(lines[i])
                i += 1
            out.append('<pre class="code">' + html.escape('\n'.join(code)) + '</pre>')
        elif stripped.startswith('|'):
            flush()
            table = []
            while i < len(lines) and lines[i].strip().startswith('|'):
                table.append(lines[i].strip())
                i += 1
            out.append(md_table(table))
            continue
        elif stripped.startswith('- '):
            flush()
            items = []
            while i < len(lines) and lines[i].strip().startswith('- '):
                items.append('<li>' + md_inline(lines[i].strip()[2:]) + '</li>')
                i += 1
            out.append('<ul>' + ''.join(items) + '</ul>')
            continue
        elif re.match(r'^\d+\.\s', stripped):
            flush()
            items = []
            while i < len(lines) and re.match(r'^\d+\.\s', lines[i].strip()):
                items.append('<li>' + md_inline(re.sub(r'^\d+\.\s', '', lines[i].strip())) + '</li>')
                i += 1
            out.append('<ol>' + ''.join(items) + '</ol>')
            continue
        elif stripped.startswith('### '):
            flush()
            out.append('<h4>' + md_inline(stripped[4:]) + '</h4>')
        elif not stripped:
            flush()
        else:
            buf.append(stripped)
        i += 1
    flush()
    return ''.join(out)


def md_table(rows):
    cells = [[c.strip() for c in r.strip('|').split('|')] for r in rows]
    if len(cells) >= 2 and all(set(c) <= set('-: ') for c in cells[1]):
        head, body = cells[0], cells[2:]
    else:
        head, body = cells[0], cells[1:]
    h = ''.join(f'<th>{md_inline(c)}</th>' for c in head)
    b = ''.join('<tr>' + ''.join(f'<td>{md_inline(c)}</td>' for c in r) + '</tr>' for r in body)
    return f'<table class="grid"><thead><tr>{h}</tr></thead><tbody>{b}</tbody></table>'


def split_error_table(desc):
    """설명에서 '| 상태 | code | 발생 조건 |' 표만 떼어낸다."""
    if not desc:
        return '', None
    lines = desc.split('\n')
    start = None
    for idx, line in enumerate(lines):
        if line.strip().startswith('|') and '상태' in line and 'code' in line:
            start = idx
            break
    if start is None:
        return desc, None
    end = start
    while end < len(lines) and lines[end].strip().startswith('|'):
        end += 1
    return '\n'.join(lines[:start] + lines[end:]), '\n'.join(lines[start:end])


# ── 표 렌더링 ───────────────────────────────────────────────
def field_table(rows, empty='필드 없음'):
    if not rows:
        return f'<p class="muted">{empty}</p>'
    show_req = any(r['required'] for r in rows)
    body = ''
    for r in rows:
        req = f'<td class="c">{r["required"]}</td>' if show_req else ''
        body += (f'<tr><td class="k">{html.escape(r["name"])}</td><td class="t">{r["type"]}</td>'
                 f'{req}<td>{r["desc"]}</td></tr>')
    head = '<th>항목명</th><th>타입</th>' + ('<th>필수</th>' if show_req else '') + '<th>설명</th>'
    return (f'<table class="grid fields"><thead><tr>{head}</tr></thead><tbody>'
            + body + '</tbody></table>')


def param_table(params):
    if not params:
        return '<p class="muted">파라미터 없음</p>'
    body = ''
    for p in params:
        p, _ = deref(p)
        s, _ = merge(p.get('schema', {}))
        bits = constraints(s)
        if s.get('example') is not None:
            bits.append(f"예) <code>{html.escape(str(s['example']))}</code>")
        desc = md_inline(p.get('description', '').strip()) if p.get('description') else ''
        full = ' · '.join([b for b in [desc] + bits if b])
        body += (f'<tr><td class="k">{html.escape(p["name"])}</td>'
                 f'<td class="c">{p["in"]}</td><td class="t">{type_label(p.get("schema", {}))}</td>'
                 f'<td class="c">{"Y" if p.get("required") else ""}</td><td>{full}</td></tr>')
    return ('<table class="grid fields"><thead><tr><th>항목명</th><th>위치</th><th>타입</th>'
            '<th>필수</th><th>설명</th></tr></thead><tbody>' + body + '</tbody></table>')


def error_codes_of(response_node):
    """components/responses 를 가리키는 응답에서 errorCode 목록을 뽑는다."""
    node, _ = deref(response_node)
    codes = []
    content = (node.get('content') or {}).get('application/json') or {}
    for key, ex in (content.get('examples') or {}).items():
        val = (ex or {}).get('value') or {}
        err = val.get('error') or {}
        if err.get('code'):
            codes.append(err['code'])
        elif key.isupper():
            codes.append(key)
    return codes


def json_block(value):
    return '<pre class="code json">' + html.escape(
        json.dumps(value, ensure_ascii=False, indent=2)) + '</pre>'


def first_example(media):
    exs = media.get('examples') or {}
    for key, ex in exs.items():
        return key, (ex or {}).get('summary'), (ex or {}).get('value')
    if 'example' in media:
        return None, None, media['example']
    return None, None, None


# ── 문서 조립 ───────────────────────────────────────────────
CSS = """
@page { size: A4; margin: 15mm 14mm 16mm 14mm; }
* { box-sizing: border-box; }
body {
  font-family: "Apple SD Gothic Neo", "Pretendard", "Malgun Gothic", -apple-system, sans-serif;
  font-size: 9.4pt; line-height: 1.55; color: #14171a; margin: 0;
  -webkit-print-color-adjust: exact; print-color-adjust: exact;
}
code, pre { font-family: "SFMono-Regular", Menlo, Consolas, monospace; }
code { font-size: 0.88em; background: #f1f3f5; padding: 0.5px 3px; border-radius: 3px; color: #1f2b3a; }
pre.code {
  background: #f8f9fa; border: 1px solid #e3e6ea; border-left: 3px solid #7a8b9c;
  padding: 7px 9px; font-size: 8.1pt; line-height: 1.45; white-space: pre-wrap;
  word-break: break-all; border-radius: 3px; margin: 5px 0 9px;
}
pre.code.json { border-left-color: #3a6ea5; }
a { color: #1a4d7a; text-decoration: none; }
p { margin: 5px 0; }
ul, ol { margin: 5px 0 5px 0; padding-left: 18px; }
li { margin: 2px 0; }

/* 표지 */
.cover { min-height: 240mm; padding-top: 76mm; page-break-after: always; }
.cover .eyebrow { font-size: 10pt; letter-spacing: 3px; color: #6b7783; text-transform: uppercase; }
.cover h1 { font-size: 30pt; margin: 8px 0 2px; letter-spacing: -0.5px; }
.cover .sub { font-size: 12pt; color: #47525d; margin-bottom: 26px; }
.cover .rule { height: 3px; background: #14171a; width: 62px; margin: 14px 0 22px; }
.cover table { width: 100%; border-collapse: collapse; font-size: 9.4pt; }
.cover table td { padding: 6px 0; border-bottom: 1px solid #e3e6ea; }
.cover table td:first-child { width: 110px; color: #6b7783; }
.cover .note { margin-top: 26px; font-size: 8.6pt; color: #47525d; background: #f8f9fa;
  border-left: 3px solid #c9d1d9; padding: 9px 11px; }

/* 제목 */
h2 { font-size: 15pt; margin: 0 0 10px; padding-bottom: 5px; border-bottom: 2px solid #14171a;
  page-break-after: avoid; page-break-before: always; }
h2.first { page-break-before: avoid; }
h3 { font-size: 11.2pt; margin: 16px 0 6px; page-break-after: avoid; }
h4 { font-size: 9.6pt; margin: 10px 0 4px; color: #2c3742; page-break-after: avoid; }
.sec-label { font-size: 8.4pt; font-weight: 700; letter-spacing: 1.4px; color: #6b7783;
  margin: 12px 0 4px; page-break-after: avoid; }

/* 목차 */
.toc { column-count: 2; column-gap: 22px; font-size: 9pt; }
.toc div { margin: 2.5px 0; break-inside: avoid; }
.toc .t1 { font-weight: 700; margin-top: 9px; }
.toc .t2 { padding-left: 12px; color: #2c3742; }
.toc .num { display: inline-block; min-width: 26px; color: #6b7783; }

/* 오퍼레이션 */
.op { page-break-inside: auto; margin-bottom: 16px; }
.endpoint { display: flex; align-items: center; gap: 7px; background: #f8f9fa;
  border: 1px solid #e3e6ea; border-radius: 4px; padding: 5px 8px; margin: 5px 0 7px;
  font-size: 9pt; page-break-inside: avoid; }
.badge { font-family: "SFMono-Regular", Menlo, monospace; font-size: 7.6pt; font-weight: 700;
  color: #fff; padding: 2px 6px; border-radius: 3px; letter-spacing: 0.4px; }
.GET { background: #2f7d4f; } .POST { background: #2b5f9e; }
.PATCH { background: #9a6b12; } .DELETE { background: #a33a3a; } .PUT { background: #7a4fa3; }
.path { font-family: "SFMono-Regular", Menlo, monospace; font-size: 8.8pt; }
.auth { margin-left: auto; font-size: 7.8pt; color: #47525d; background: #fff;
  border: 1px solid #d6dbe0; border-radius: 10px; padding: 1px 8px; white-space: nowrap; }

/* 표 */
table.grid { width: 100%; border-collapse: collapse; margin: 4px 0 9px; font-size: 8.5pt;
  page-break-inside: avoid; }
table.grid th, table.grid td { border: 1px solid #dfe3e8; padding: 4px 6px; vertical-align: top;
  text-align: left; }
table.grid th { background: #eef1f4; font-weight: 700; color: #2c3742; }
table.grid td.k { font-family: "SFMono-Regular", Menlo, monospace; font-size: 8.1pt;
  white-space: nowrap; color: #1f2b3a; }
table.grid td.t { font-family: "SFMono-Regular", Menlo, monospace; font-size: 7.8pt; color: #47525d;
  white-space: nowrap; }
table.grid td.c { text-align: center; white-space: nowrap; }
table.grid.fields th:nth-child(1) { width: 22%; }
table.grid.fields th:nth-child(2) { width: 15%; }
table.grid.fields th:nth-child(3) { width: 7%; }
.enumvals { font-size: 7.2pt; color: #6b7783; }
.muted { color: #6b7783; font-size: 8.6pt; margin: 3px 0 8px; }
.callout { background: #fdf7e3; border-left: 3px solid #c9a227; padding: 8px 11px;
  font-size: 8.7pt; margin: 8px 0; page-break-inside: avoid; }
.callout b { color: #6b4e00; }
"""


def auth_label(op):
    sec = op.get('security')
    if sec is None:
        return '없음 (비로그인 허용)'
    if any(s == {} for s in sec):
        return '선택 (로그인 시 개인화)'
    if sec:
        return 'USER (로그인 필요)'
    return '없음 (비로그인 허용)'


def collect_ops():
    """태그 선언 순서대로 오퍼레이션을 모은다."""
    order = [t['name'] for t in DOC['tags']]
    buckets = {t: [] for t in order}
    for path, item in DOC['paths'].items():
        for method in METHODS:
            if method in item:
                op = item[method]
                tag = (op.get('tags') or ['기타'])[0]
                buckets.setdefault(tag, []).append((method.upper(), path, op))
    return order, buckets


def render_operation(num, method, path, op):
    out = [f'<div class="op"><h3>{num} {html.escape(op.get("summary", ""))}</h3>']
    out.append(f'<div class="endpoint"><span class="badge {method}">{method}</span>'
               f'<span class="path">{html.escape(path)}</span>'
               f'<span class="auth">인증 · {auth_label(op)}</span></div>')

    prose, err_table = split_error_table(op.get('description', ''))
    if prose.strip():
        out.append(md_block(prose))

    # 요청
    out.append('<div class="sec-label">REQUEST</div>')
    out.append(param_table(op.get('parameters') or []))
    body = op.get('requestBody')
    req_example = None
    if body:
        media = (body.get('content') or {}).get('application/json') or {}
        schema = media.get('schema') or {}
        _, sname = merge(schema)
        out.append(f'<h4>Body — <code>{html.escape(sname or "object")}</code>'
                   f'{" (필수)" if body.get("required") else ""}</h4>')
        out.append(field_table(flatten(schema)))
        req_example = first_example(media)
    else:
        out.append('<p class="muted">Body 없음</p>')

    # 응답
    out.append('<div class="sec-label">RESPONSE</div>')
    res_example = None
    for status, res in sorted(op['responses'].items()):
        if not status.startswith('2'):
            continue
        res, _ = deref(res)
        desc = res.get('description', '').strip().split('\n')[0]
        out.append(f'<h4>{status} — {md_inline(desc)}</h4>')
        media = (res.get('content') or {}).get('application/json')
        if not media:
            out.append('<p class="muted">응답 본문 없음</p>')
            continue
        schema = media.get('schema') or {}
        merged, ename = merge(schema)
        data_schema = (merged.get('properties') or {}).get('data')
        if data_schema is not None:
            dm, dname = merge(data_schema)
            label = dname or type_label(data_schema)
            out.append(f'<p class="muted">공통 봉투 <code>{{ success, data, error }}</code> 의 '
                       f'<code>data</code> = <code>{html.escape(label)}</code></p>')
            if dm.get('type') == 'array':
                out.append(field_table(flatten(dm.get('items', {}), prefix='[].'),
                                       empty='원시값 배열'))
            elif (dname or '').startswith('Page'):
                out.append('<p class="muted">페이지 메타 필드(<code>number</code> · <code>size</code> · '
                           '<code>totalElements</code> · <code>totalPages</code> · <code>first</code> · '
                           '<code>last</code> · <code>sort</code> · <code>pageable</code> 등)는 '
                           '「1.3 페이지네이션」과 같습니다. 아래는 <code>content[]</code> 항목입니다.</p>')
                content_schema, _ = merge((dm.get('properties') or {}).get('content', {}))
                out.append(field_table(flatten(content_schema.get('items', {}),
                                               prefix='content[].')))
            else:
                out.append(field_table(flatten(data_schema)))
        else:
            out.append(field_table(flatten(schema)))
        if res_example is None:
            res_example = first_example(media)

    # 예외
    out.append('<div class="sec-label">예외 · 오류 응답</div>')
    if err_table:
        out.append(md_table([l.strip() for l in err_table.split('\n') if l.strip()]))
    rows = [] if err_table else []
    for status, res in sorted(op['responses'].items()):
        if status.startswith('2'):
            continue
        node, _ = deref(res)
        codes = error_codes_of(res)
        rows.append(f'<tr><td class="c">{status}</td>'
                    f'<td>{md_inline(node.get("description", "").strip().split(chr(10))[0])}</td>'
                    f'<td class="k">{" · ".join(codes) or "-"}</td></tr>')
    if rows and not err_table:
        out.append('<table class="grid"><thead><tr><th style="width:9%">상태</th><th>의미</th>'
                   '<th style="width:36%">errorCode</th></tr></thead><tbody>'
                   + ''.join(rows) + '</tbody></table>')

    # 예시
    if req_example and req_example[2] is not None or res_example and res_example[2] is not None:
        out.append('<div class="sec-label">예시</div>')
    if req_example and req_example[2] is not None:
        title = req_example[1] or '요청'
        out.append(f'<h4>Request — {html.escape(title)}</h4>{json_block(req_example[2])}')
    if res_example and res_example[2] is not None:
        title = res_example[1] or '응답'
        out.append(f'<h4>Response — {html.escape(title)}</h4>{json_block(res_example[2])}')

    out.append('</div>')
    return ''.join(out)


def status_map_for_components():
    """components/responses 가 실제로 어떤 상태코드에 붙어 있는지 역추적한다."""
    m = {}
    for item in DOC['paths'].values():
        for method in METHODS:
            op = item.get(method)
            if not op:
                continue
            for status, res in op['responses'].items():
                if isinstance(res, dict) and '$ref' in res:
                    m.setdefault(res['$ref'].split('/')[-1], set()).add(status)
    return m


def build():
    order, buckets = collect_ops()
    total = sum(len(v) for v in buckets.values())
    info = DOC['info']

    # ── 표지 ────────────────────────────────────────────────
    servers = '<br>'.join(f"{s['description']} — <code>{s['url']}</code>" for s in DOC['servers'])
    parts = [f"""<div class="cover">
  <div class="eyebrow">REST API Specification</div>
  <h1>ZipSa API 명세서</h1>
  <div class="sub">청년 주거정책 통합 플랫폼 · 백엔드 인터페이스 규격</div>
  <div class="rule"></div>
  <table>
    <tr><td>버전</td><td>v{info['version']} (OpenAPI {DOC['openapi']})</td></tr>
    <tr><td>오퍼레이션</td><td>{total}개 · {len(DOC['paths'])} 경로 · {len(order)} 도메인</td></tr>
    <tr><td>기준</td><td>백엔드 구현 코드 (<code>com.zipsa.**.*Controller</code>)</td></tr>
    <tr><td>원본</td><td><code>docs/api/API.yml</code></td></tr>
    <tr><td>서버</td><td>{servers}</td></tr>
    <tr><td>작성일</td><td>{datetime.date.today().isoformat()}</td></tr>
  </table>
  <div class="note">
    이 문서는 <code>docs/api/API.yml</code> 에서 자동 생성했습니다.
    내용을 고칠 때는 이 PDF 가 아니라 <b>API.yml 을 수정한 뒤 다시 생성</b>하세요.
    Swagger Editor(editor.swagger.io) 나 Postman 에 같은 파일을 그대로 가져갈 수 있습니다.
  </div>
</div>"""]

    # ── 목차 ────────────────────────────────────────────────
    toc = ['<div class="t1"><span class="num">1</span>공통 규약</div>',
           '<div class="t1"><span class="num">2</span>공통 오류 코드</div>',
           '<div class="t1"><span class="num">3</span>공통 Enum</div>']
    sec = 3
    numbered = []
    for tag in order:
        ops = buckets.get(tag) or []
        if not ops:
            continue
        sec += 1
        tag_desc = next((t.get('description', '') for t in DOC['tags'] if t['name'] == tag), '')
        toc.append(f'<div class="t1"><span class="num">{sec}</span>{tag} — {html.escape(tag_desc)}</div>')
        entries = []
        for i, (method, path, op) in enumerate(ops, 1):
            num = f'{sec}.{i}'
            entries.append((num, method, path, op))
            toc.append(f'<div class="t2"><span class="num">{num}</span>'
                       f'{html.escape(op.get("summary", ""))}</div>')
        numbered.append((sec, tag, tag_desc, entries))

    parts.append('<h2 class="first">목차</h2><div class="toc">' + ''.join(toc) + '</div>')

    # ── 1. 공통 규약 ────────────────────────────────────────
    parts.append('<h2>1. 공통 규약</h2>')
    parts.append(md_block(info['description']))
    parts.append('<h3>1.1 인증 방식</h3>')
    scheme = DOC['components']['securitySchemes']['bearerAuth']
    parts.append('<table class="grid"><tbody>'
                 f'<tr><th style="width:22%">타입</th><td>{scheme["type"]} / {scheme["scheme"]} '
                 f'({scheme["bearerFormat"]})</td></tr>'
                 f'<tr><th>전달</th><td>{md_inline(scheme["description"])}</td></tr>'
                 '<tr><th>Access 만료</th><td>30분</td></tr>'
                 '<tr><th>Refresh 만료</th><td>14일</td></tr>'
                 '<tr><th>표기</th><td>각 오퍼레이션 우측 상단의 <b>인증</b> 배지 — '
                 '<code>없음</code> 비로그인 허용 / <code>선택</code> 로그인 시 개인화 / '
                 '<code>USER</code> 로그인 필수</td></tr></tbody></table>')
    parts.append('<h3>1.2 공통 응답 봉투</h3>')
    parts.append(field_table(flatten(SCHEMAS['ApiResponse'])))
    parts.append('<h3>1.3 페이지네이션</h3>')
    parts.append('<h4>요청 파라미터</h4>')
    parts.append(param_table([PARAMS['PageParam'], PARAMS['SizeParam']]))
    parts.append('<h4>응답 (<code>data</code> 자리)</h4>')
    parts.append(field_table([r for r in flatten(SCHEMAS['Page']) if '.' not in r['name']]))

    # ── 2. 공통 오류 코드 ───────────────────────────────────
    smap = status_map_for_components()
    rows = []
    for name, res in RESPONSES.items():
        statuses = sorted(smap.get(name, []))
        content = (res.get('content') or {}).get('application/json') or {}
        for key, ex in (content.get('examples') or {}).items():
            err = ((ex or {}).get('value') or {}).get('error') or {}
            if not err:
                continue
            rows.append((err.get('code', key), ' · '.join(statuses),
                         err.get('message', ''), name))
    seen, uniq = set(), []
    for code, st, msg, name in rows:
        if code in seen:
            continue
        seen.add(code)
        uniq.append((code, st, msg, name))
    uniq.sort(key=lambda r: (r[1], r[0]))
    body = ''.join(f'<tr><td class="k">{c}</td><td class="c">{st}</td><td>{html.escape(m)}</td>'
                   f'<td class="t">{n}</td></tr>' for c, st, m, n in uniq)
    parts.append('<h2>2. 공통 오류 코드</h2>')
    parts.append('<p>실패 응답은 모두 같은 형태입니다. '
                 '<code>{ "success": false, "data": null, "error": { "code", "message" } }</code></p>')
    parts.append('<table class="grid"><thead><tr><th style="width:26%">errorCode</th>'
                 '<th style="width:9%">HTTP</th><th>기본 메시지</th>'
                 '<th style="width:20%">응답 스키마</th></tr></thead><tbody>'
                 + body + '</tbody></table>')
    parts.append('<div class="callout"><b>주의</b> — 현재 구현은 <code>@Valid</code> 검증 실패만 400 으로 '
                 '변환합니다. 필수 쿼리파라미터 누락·타입 불일치·JSON 파싱 실패는 '
                 '<code>500 INTERNAL_ERROR</code> 로 나갑니다. 이 문서의 400 표기는 '
                 '<b>지향하는 계약</b>이며, <code>GlobalExceptionHandler</code> 에 핸들러 추가가 필요합니다.</div>')

    # ── 3. 공통 Enum ────────────────────────────────────────
    parts.append('<h2>3. 공통 Enum</h2>')
    erows = ''
    for name, s in SCHEMAS.items():
        if s.get('type') == 'string' and 'enum' in s:
            vals = ' · '.join(f'<code>{v}</code>' for v in s['enum'] if v is not None)
            erows += (f'<tr><td class="k">{name}</td><td>{vals}</td>'
                      f'<td>{md_inline(s.get("description", ""))}</td></tr>')
    parts.append('<table class="grid"><thead><tr><th style="width:17%">Enum</th>'
                 '<th style="width:43%">값</th><th>설명</th></tr></thead><tbody>'
                 + erows + '</tbody></table>')

    # ── 4~ 도메인별 오퍼레이션 ──────────────────────────────
    for sec, tag, tag_desc, entries in numbered:
        parts.append(f'<h2>{sec}. {tag} — {html.escape(tag_desc)}</h2>')
        summary = ''.join(
            f'<tr><td class="c"><span class="badge {m}">{m}</span></td>'
            f'<td class="k">{html.escape(p)}</td><td>{html.escape(o.get("summary", ""))}</td>'
            f'<td class="c">{auth_label(o).split(" ")[0]}</td></tr>'
            for n, m, p, o in entries)
        parts.append('<table class="grid"><thead><tr><th style="width:9%">Method</th>'
                     '<th style="width:34%">Path</th><th>설명</th>'
                     '<th style="width:11%">인증</th></tr></thead><tbody>'
                     + summary + '</tbody></table>')
        for num, method, path, op in entries:
            parts.append(render_operation(num, method, path, op))

    return f"""<!doctype html><html lang="ko"><head><meta charset="utf-8">
<title>ZipSa API 명세서 v{info['version']}</title><style>{CSS}</style></head>
<body>{''.join(parts)}</body></html>"""



# ── 렌더링 ──────────────────────────────────────────────────
FOOTER = (
    '<div style="width:100%;font-size:7pt;color:#8a949e;padding:0 14mm;'
    'font-family:-apple-system,sans-serif;display:flex;justify-content:space-between;">'
    '<span>ZipSa API 명세서 v{version}</span>'
    '<span><span class="pageNumber"></span> / <span class="totalPages"></span></span></div>'
)

NODE_SCRIPT = """
const puppeteer = require('puppeteer-core');
(async () => {
  const [src, out, chrome, footer] = process.argv.slice(2);
  const browser = await puppeteer.launch({ executablePath: chrome, headless: 'new',
    args: ['--no-sandbox', '--font-render-hinting=none'] });
  const page = await browser.newPage();
  await page.goto('file://' + src, { waitUntil: 'networkidle0' });
  await page.pdf({ path: out, format: 'A4', printBackground: true,
    displayHeaderFooter: true, headerTemplate: '<div></div>', footerTemplate: footer,
    margin: { top: '15mm', right: '14mm', bottom: '16mm', left: '14mm' } });
  await browser.close();
})();
"""


def find_chrome():
    if pathlib.Path(CHROME).exists():
        return CHROME
    return shutil.which('google-chrome') or shutil.which('chromium')


def render_with_puppeteer(chrome):
    """페이지 번호가 있는 꼬리말을 붙인다. npm 이 없으면 조용히 포기한다."""
    if not shutil.which('npm'):
        return False
    try:
        if not (NODE_CACHE / 'node_modules/puppeteer-core').exists():
            NODE_CACHE.mkdir(parents=True, exist_ok=True)
            subprocess.run(['npm', 'install', '--silent', '--no-audit', '--no-fund',
                            'puppeteer-core@23'], cwd=NODE_CACHE, check=True,
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=300)
        script = NODE_CACHE / 'topdf.js'
        script.write_text(NODE_SCRIPT, encoding='utf-8')
        subprocess.run(['node', str(script), str(HTML_OUT), str(PDF), chrome,
                        FOOTER.format(version=DOC['info']['version'])],
                       check=True, stdout=subprocess.DEVNULL, timeout=300)
        return True
    except Exception as e:                      # noqa: BLE001 - 실패하면 CLI 로 떨어진다
        print(f'  puppeteer 사용 불가({e}) — Chrome CLI 로 대체합니다(꼬리말 없음).')
        return False


def render_with_chrome_cli(chrome):
    # 꼬리말을 끄지 않으면 Chrome 이 file:// 전체 경로를 인쇄한다.
    subprocess.run([chrome, '--headless=new', '--disable-gpu', '--no-pdf-header-footer',
                    f'--print-to-pdf={PDF}', '--virtual-time-budget=10000', HTML_OUT.as_uri()],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def main():
    HTML_OUT.write_text(build(), encoding='utf-8')
    chrome = find_chrome()
    if not chrome:
        sys.exit(f'Chrome 을 찾지 못했습니다. HTML 만 만들었습니다: {HTML_OUT}')
    if not render_with_puppeteer(chrome):
        render_with_chrome_cli(chrome)
    ops = sum(1 for i in DOC['paths'].values() for m in i if m in METHODS)
    print(f'{PDF.relative_to(ROOT)} — 오퍼레이션 {ops}개, {PDF.stat().st_size // 1024}KB')


if __name__ == '__main__':
    main()
