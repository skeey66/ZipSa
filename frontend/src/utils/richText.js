/**
 * 정책 본문을 읽기 좋은 블록으로 쪼갭니다.
 *
 * 원문이 이런 형태로 옵니다(공공기관이 한글 문서에서 그대로 옮긴 것):
 *
 *   주거급여 수급가구원 중 ... 자립 도모
 *
 *   ○ (지원내용) 청년 주소지 기준임대료 상한으로 실제임차료 지급
 *    - (대상연령) 만19세~30세 미만 미혼 청년
 *    ※ 청년가구원 연령 기준: ...
 *   ○ (지원절차)
 *    ① 청년주거급여 신청
 *
 * 그대로 pre-wrap 으로 뿌리면 전부 같은 크기의 글자 덩어리가 되어 읽히지 않습니다.
 * 기호를 의미로 바꿔서 계층을 만듭니다.
 */

const SECTION = /^\s*[○◯●□■]\s*/
const ITEM = /^\s*[-–·•]\s+/
const NOTE = /^\s*[※*]\s*/
const STEP = /^\s*([①②③④⑤⑥⑦⑧⑨⑩]|\d+[).])\s*/
// "○ (지원내용) 본문" 에서 괄호 라벨만 떼어냅니다.
const LABEL = /^\(([^)]{1,20})\)\s*/

export function parseRichText(raw) {
  if (!raw) return []

  const blocks = []
  let leadTaken = false

  for (const line of raw.split('\n')) {
    const text = line.trim()
    if (!text) continue

    if (SECTION.test(line)) {
      const rest = line.replace(SECTION, '').trim()
      const m = rest.match(LABEL)
      blocks.push({
        type: 'section',
        label: m ? m[1] : null,
        text: m ? rest.replace(LABEL, '').trim() : rest,
      })
      continue
    }

    if (STEP.test(line)) {
      const m = line.match(STEP)
      blocks.push({ type: 'step', marker: m[1], text: line.replace(STEP, '').trim() })
      continue
    }

    if (NOTE.test(line)) {
      blocks.push({ type: 'note', text: line.replace(NOTE, '').trim() })
      continue
    }

    if (ITEM.test(line)) {
      const rest = line.replace(ITEM, '').trim()
      const m = rest.match(LABEL)
      blocks.push({
        type: 'item',
        label: m ? m[1] : null,
        text: m ? rest.replace(LABEL, '').trim() : rest,
      })
      continue
    }

    // 기호 없이 처음 나오는 문단은 개요로 봅니다. 이후의 맨 문단은 일반 문단.
    blocks.push({ type: leadTaken ? 'para' : 'lead', text })
    leadTaken = true
  }

  return blocks
}
