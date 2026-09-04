"""개발용 더미 데이터 생성기.

    python scripts/seed_dev_data.py            # 생성
    python scripts/seed_dev_data.py --reset    # 기존 시드 삭제 후 재생성
    python scripts/seed_dev_data.py --clean    # 삭제만

⚠️ 개발·시연 전용입니다. 운영 DB 에서 실행하지 마세요.
   실제 정책 데이터는 공공데이터포털 API 로 crawler 가 채웁니다.

■ 이 스크립트가 만드는 것
    정책 36건, 회원 160명, 정책 조회로그 ~3,000건, 찜, 게시글·댓글·좋아요

■ 왜 무작위가 아닌가
    조회 로그를 균등 난수로 뿌리면 "비슷한 사람이 많이 본 정책" 추천이
    그냥 인기순과 같아집니다. 그래서 회원을 4개 프로필 군집으로 나누고,
    군집마다 선호 정책 풀을 다르게 줬습니다. 추천 로직이 이 심어둔 패턴을
    되찾아내는지가 곧 검증입니다. (기대 결과는 파일 하단 EXPECTED 참고)

■ 재현성
    시드 고정이라 누가 몇 번을 돌려도 같은 데이터가 나옵니다.
"""

from __future__ import annotations

import argparse
import os
import random
import sys
from datetime import date, datetime, timedelta
from pathlib import Path

import psycopg
from dotenv import load_dotenv

ROOT = Path(__file__).resolve().parents[1]
load_dotenv(ROOT / ".env")

SEED = 20260903
LOGIN_PREFIX = "seed_user_"
EXT_PREFIX = "SEED-POLICY-"

# 모든 더미 계정 공용 비밀번호: Demo!pass12  (BCrypt, 개발 전용)
DUMMY_PASSWORD_HASH = "$2a$10$jKfn23da3SQX0/rBRJAS9esxMwH6sIhuM3M3jC3mesxwOCxAVZ9FK"

# ────────────────────────────────────────────────────────────
#  정책 마스터
#    tag 는 DB 컬럼이 아니라 군집 선호도를 계산하기 위한 생성기 전용 라벨입니다.
# ────────────────────────────────────────────────────────────
# (tag, title, category, region, issuer)
POLICIES = [
    # ── 월세·주거비 (사회초년생/학생) ──
    ("rent", "청년월세 한시 특별지원", "HOUSING", "전국", "국토교통부"),
    ("rent", "서울시 청년월세지원", "HOUSING", "서울", "서울특별시"),
    ("rent", "경기도 청년 월세 지원사업", "HOUSING", "경기", "경기도"),
    ("rent", "부산 청년 월세 지원", "HOUSING", "부산", "부산광역시"),
    ("rent", "청년 주거급여 분리지급", "HOUSING", "전국", "국토교통부"),
    ("rent", "대학생 주거안정 장학금", "HOUSING", "전국", "한국장학재단"),
    ("rent", "인천 청년 주거비 지원", "HOUSING", "인천", "인천광역시"),
    ("rent", "청년 이사비 지원사업", "HOUSING", "서울", "서울특별시"),
    # ── 전세·매입 대출 (직장인) ──
    ("loan", "중소기업 취업청년 전월세보증금 대출", "LOAN", "전국", "주택도시기금"),
    ("loan", "청년전용 버팀목전세자금대출", "LOAN", "전국", "주택도시기금"),
    ("loan", "청년전용 보증부월세대출", "LOAN", "전국", "주택도시기금"),
    ("loan", "내집마련 디딤돌대출", "LOAN", "전국", "주택도시기금"),
    ("loan", "청년 전세보증금 반환보증 보증료 지원", "LOAN", "전국", "HUG"),
    ("loan", "서울시 청년 임차보증금 이자지원", "LOAN", "서울", "서울특별시"),
    ("loan", "청년도약계좌", "LOAN", "전국", "서민금융진흥원"),
    ("loan", "청년 햇살론유스", "LOAN", "전국", "서민금융진흥원"),
    # ── 신혼·가족 (기혼) ──
    ("newlywed", "신혼부부 전세임대", "PUBLIC_HOUSING", "전국", "LH"),
    ("newlywed", "신혼희망타운 공공분양", "SUPPLY", "전국", "LH"),
    ("newlywed", "신혼부부 전용 전세자금대출", "LOAN", "전국", "주택도시기금"),
    ("newlywed", "신생아 특례 구입자금 대출", "LOAN", "전국", "주택도시기금"),
    ("newlywed", "서울시 신혼부부 임차보증금 이자지원", "LOAN", "서울", "서울특별시"),
    ("newlywed", "다자녀 가구 주택 특별공급", "SUPPLY", "전국", "국토교통부"),
    # ── 공공임대 (전 계층, 저소득 편중) ──
    ("public", "행복주택 청년 공급", "PUBLIC_HOUSING", "전국", "LH"),
    ("public", "청년 매입임대주택", "PUBLIC_HOUSING", "전국", "LH"),
    ("public", "기존주택 전세임대 Ⅰ 유형", "PUBLIC_HOUSING", "전국", "LH"),
    ("public", "국민임대주택 공급", "PUBLIC_HOUSING", "전국", "LH"),
    ("public", "역세권 청년주택", "PUBLIC_HOUSING", "서울", "서울특별시"),
    ("public", "공공기숙사 입주자 모집", "PUBLIC_HOUSING", "서울", "한국장학재단"),
    ("public", "경기 청년 매입임대", "PUBLIC_HOUSING", "경기", "경기주택도시공사"),
    # ── 창업·자영업 (프리랜서/자영업) ──
    ("startup", "청년창업사관학교", "SUPPLY", "전국", "중소벤처기업부"),
    ("startup", "청년 소상공인 융자지원", "LOAN", "전국", "소상공인시장진흥공단"),
    ("startup", "1인 자영업자 고용보험료 지원", "SUPPLY", "전국", "고용노동부"),
    ("startup", "프리랜서 안전망 지원사업", "SUPPLY", "서울", "서울특별시"),
    # ── 취업준비 (취준생) ──
    ("jobseek", "국민취업지원제도 청년특례", "SUPPLY", "전국", "고용노동부"),
    ("jobseek", "청년내일채움공제", "SUPPLY", "전국", "고용노동부"),
    ("jobseek", "서울 청년수당", "SUPPLY", "서울", "서울특별시"),
]

# ────────────────────────────────────────────────────────────
#  회원 프로필 군집
#    weights: 이 군집이 각 tag 정책을 볼 상대 확률
# ────────────────────────────────────────────────────────────
CLUSTERS = [
    {
        "key": "A",
        "label": "20대 초반 · 미혼 · 학생/취준생 · 저소득",
        "n": 40,
        "age": ["AGE_20S_EARLY"],
        "marital": ["SINGLE"],
        "job": ["STUDENT", "JOB_SEEKER"],
        "salary": ["UNDER_2000", "RANGE_2000_3000"],
        "weights": {"rent": 34, "jobseek": 26, "public": 20, "loan": 12, "startup": 5, "newlywed": 3},
    },
    {
        "key": "B",
        "label": "20대 후반 · 미혼 · 직장인 · 3~4천",
        "n": 50,
        "age": ["AGE_20S_LATE"],
        "marital": ["SINGLE"],
        "job": ["EMPLOYEE"],
        "salary": ["RANGE_2000_3000", "RANGE_3000_4000", "RANGE_4000_5000"],
        "weights": {"loan": 40, "rent": 22, "public": 20, "jobseek": 10, "startup": 5, "newlywed": 3},
    },
    {
        "key": "C",
        "label": "30대 · 기혼 · 직장인 · 고소득",
        "n": 40,
        "age": ["AGE_30S_EARLY", "AGE_30S_LATE"],
        "marital": ["MARRIED"],
        "job": ["EMPLOYEE"],
        "salary": ["RANGE_4000_5000", "RANGE_5000_7000", "OVER_7000"],
        "weights": {"newlywed": 45, "loan": 25, "public": 18, "rent": 6, "startup": 4, "jobseek": 2},
    },
    {
        "key": "D",
        "label": "30대 · 미혼 · 자영업/프리랜서",
        "n": 30,
        "age": ["AGE_30S_EARLY", "AGE_30S_LATE"],
        "marital": ["SINGLE"],
        "job": ["SELF_EMPLOYED", "ETC"],
        "salary": ["RANGE_2000_3000", "RANGE_3000_4000", "RANGE_5000_7000"],
        "weights": {"startup": 38, "loan": 26, "public": 18, "rent": 12, "jobseek": 4, "newlywed": 2},
    },
]

ADJ = ["든든한", "성실한", "느긋한", "부지런한", "조용한", "다정한", "씩씩한", "총명한", "소박한",
       "명랑한", "차분한", "야무진", "포근한", "당당한", "슬기로운", "정직한", "상냥한", "새침한",
       "우직한", "발랄한"]
NOUN = ["감자", "달빛", "청포도", "구름", "고양이", "너구리", "바람", "보름달", "은행나무", "청귤",
        "산책", "겨울밤", "라디오", "우산", "종이배", "모과", "다람쥐", "물결", "노을", "미역국"]

POST_TITLES = [
    ("LOAN", "중기청 100% 승인 후기 정리해봤습니다",
     "작년에 떨어지고 올해 재신청해서 통과했습니다.\n\n"
     "1) 재직 6개월을 채우고 나서 넣은 게 컸습니다. 첫 시도 때는 3개월이었어요.\n"
     "2) 집주인 동의가 제일 오래 걸립니다. 계약 전에 미리 물어보세요.\n"
     "3) 은행 지점마다 안내가 달라서 두 곳에서 상담받고 비교했습니다.\n\n"
     "서류는 재직증명서, 소득금액증명원, 등기부등본, 계약서 사본이었습니다."),
    ("LOAN", "버팀목 vs 중기청 뭐가 나은가요?",
     "둘 다 조건은 되는데 어느 쪽이 나은지 모르겠습니다.\n\n"
     "중기청이 금리는 훨씬 싼데 대상 주택 조건이 빡빡하다고 하고,\n"
     "버팀목은 한도가 더 나온다고 들었습니다.\n\n"
     "보증금 1억 2천 정도 생각하고 있는데 경험 있으신 분 계실까요?"),
    ("LOAN", "전세보증보험 꼭 들어야 하나요",
     "보증금이 전세가율 대비 낮은 편이라 안 들어도 되나 고민 중입니다.\n\n"
     "주변에서는 무조건 들라고 하는데 보증료가 아깝기도 하고…\n"
     "실제로 사고 겪으신 분 이야기 들어보고 싶습니다."),
    ("LOAN", "디딤돌 대출 서류 목록 공유",
     "은행 갈 때마다 서류 빠뜨려서 두 번 갔습니다. 정리해둡니다.\n\n"
     "- 주민등록등본 (3개월 이내)\n- 가족관계증명서\n- 소득금액증명원\n"
     "- 재직증명서\n- 매매계약서 사본\n- 등기부등본\n\n"
     "혼인관계증명서는 미혼이면 안 물어보는 곳도 있었습니다."),
    ("LOAN", "청년도약계좌 3년차 후기",
     "3년째 넣고 있습니다. 중간에 소득이 늘어서 정부기여금이 줄었는데,\n"
     "그래도 일반 적금보다는 낫다고 생각합니다.\n\n"
     "다만 5년을 못 채우면 손해가 커서, 넣기 전에 유지 가능한 금액인지\n"
     "꼭 계산해보시는 게 좋겠습니다."),

    ("INFO", "행복주택 신청 자격 헷갈리는 부분 정리",
     "공고문 읽다가 헷갈렸던 것들 정리합니다.\n\n"
     "○ 소득 기준은 '가구' 기준입니다. 부모님과 세대분리가 안 돼 있으면 합산됩니다.\n"
     "○ 자산 기준은 총자산과 자동차 가액을 따로 봅니다.\n"
     "○ 대학생·청년·신혼부부 유형별로 계층이 달라서 경쟁률이 크게 차이납니다.\n\n"
     "본인 유형부터 확인하고 공고를 보시는 게 빠릅니다."),
    ("INFO", "청년월세지원 신청 절차 요약본",
     "지자체마다 다르지만 큰 흐름은 비슷했습니다.\n\n"
     "1) 복지로 또는 시·군 홈페이지에서 온라인 신청\n"
     "2) 임대차계약서와 월세 이체 내역 제출\n"
     "3) 소득·재산 조사 (한 달 정도)\n"
     "4) 선정 통보 후 매월 지급\n\n"
     "이체 내역은 계좌이체 기록이 있어야 해서 현금 납부는 인정이 안 됐습니다."),
    ("INFO", "LH 공고 뜨는 요일 패턴 있더라구요",
     "체감상 화요일과 목요일에 많이 올라옵니다.\n"
     "매일 들어가기 번거로우면 그 두 날만 확인해도 크게 놓치지 않았습니다.\n\n"
     "다만 정정공고가 따로 올라오는 경우가 있어서, 신청 직전에 한 번 더 보세요."),
    ("INFO", "전입신고·확정일자 순서 헷갈리지 마세요",
     "잔금 치른 당일에 전입신고와 확정일자를 같이 받는 게 안전합니다.\n\n"
     "대항력은 전입신고 다음 날 0시부터 생기는데, 그 사이에 근저당이 잡히면\n"
     "순위가 밀립니다. 계약서에 '잔금일 다음 날까지 근저당 설정 금지' 특약을\n"
     "넣어두시면 좋습니다."),
    ("INFO", "등기부등본 보는 법 (근저당 확인)",
     "을구를 먼저 보세요. 근저당권이 있으면 채권최고액이 적혀 있습니다.\n\n"
     "보통 실제 대출액의 120% 정도로 잡히니 역산하면 대략 감이 옵니다.\n"
     "채권최고액 + 내 보증금이 시세의 70~80%를 넘으면 위험하다고 봅니다.\n\n"
     "갑구에 가압류나 경매개시결정이 있으면 계약하지 마세요."),

    ("QUESTION", "보증금 5천에 월세 40 괜찮은 조건인가요?",
     "역에서 도보 8분, 준공 12년 된 오피스텔입니다. 관리비는 7만원 별도예요.\n\n"
     "주변 시세를 잘 몰라서 판단이 안 섭니다. 실거래가 보면 비슷한 평수가\n"
     "보증금 3천에 월 45 정도로 나와 있던데, 보증금을 올리고 월세를 낮춘 게\n"
     "이득인지 모르겠습니다."),
    ("QUESTION", "반전세 계약할 때 주의할 점 있을까요",
     "전세로 있다가 집주인이 반전세로 돌리자고 합니다.\n\n"
     "보증금을 일부 돌려받고 월세를 내는 구조인데, 이때 계약서를 새로 쓰면\n"
     "확정일자도 다시 받아야 하는지 궁금합니다. 기존 대항력이 유지되나요?"),
    ("QUESTION", "집주인이 전세대출 거부하는데 방법 없나요",
     "계약하려는 집주인이 전세자금대출은 안 받는다고 합니다.\n\n"
     "질권설정이 부담스럽다는 이유인데, 설득할 방법이 있을까요?\n"
     "아니면 그냥 다른 집을 알아보는 게 맞을까요."),
    ("QUESTION", "신혼부부 특공 가점 계산 맞나 봐주세요",
     "혼인 2년차, 무주택 기간 3년, 청약통장 4년입니다.\n"
     "자녀는 없고 소득은 맞벌이 기준입니다.\n\n"
     "제가 계산하기로는 우선공급 대상은 아닌 것 같은데 맞을까요?"),
    ("QUESTION", "매입임대 순번 밀렸는데 기다려야 하나요",
     "예비 순번을 받았는데 앞에 30명 정도 있습니다.\n\n"
     "보통 이 정도면 얼마나 기다리나요? 계약 만료가 4개월 남아서\n"
     "다른 곳도 같이 알아봐야 하나 고민입니다."),

    ("FREE", "드디어 첫 자취방 계약했습니다",
     "반년 동안 발품 팔았는데 드디어 계약했습니다.\n\n"
     "처음엔 예산만 보고 다녔는데, 나중엔 채광이랑 곰팡이 여부를\n"
     "제일 먼저 보게 되더라구요. 여기 글 보면서 많이 배웠습니다.\n\n"
     "다음 주에 이사합니다. 감사합니다!"),
    ("FREE", "이사 견적 3군데 받아본 후기",
     "같은 짐인데 견적이 40만원까지 차이났습니다.\n\n"
     "- A업체: 65만원, 방문견적, 포장 포함\n"
     "- B업체: 48만원, 전화견적\n"
     "- C업체: 105만원, 대형업체\n\n"
     "결국 방문견적 받은 곳으로 했습니다. 전화견적은 당일에 추가금\n"
     "붙는 경우가 있다고 해서요."),
    ("FREE", "자취 2년차 살림 리스트 공유",
     "2년 살면서 진짜 잘 샀다고 생각하는 것만 적어봅니다.\n\n"
     "- 빨래건조대 (큰 걸로)\n- 전기포트\n- 다용도 선반\n"
     "- 실리콘 주방매트\n- 문틈 방풍 테이프 (겨울에 체감 큽니다)\n\n"
     "반대로 안 쓰게 된 건 에어프라이어랑 미니 오븐이었습니다."),
    ("FREE", "곰팡이 있는 집 거르는 법.txt",
     "낮에만 보면 잘 안 보입니다. 확인 순서 적어둡니다.\n\n"
     "1) 붙박이장 안쪽 구석과 뒷면\n2) 창틀 실리콘\n"
     "3) 화장실 천장\n4) 벽지 냄새 (곰팡이 냄새는 페인트로 덮어도 남습니다)\n\n"
     "새로 도배한 지 얼마 안 된 집은 오히려 의심해볼 만합니다."),
    ("FREE", "월세 살면서 아낀 관리비 팁",
     "관리비 내역서를 받아보는 것부터 시작했습니다.\n\n"
     "공용전기가 생각보다 크게 잡혀 있어서 관리사무소에 문의했더니\n"
     "산정 방식이 바뀌어 있더라구요. 문의하고 나서 만원 정도 줄었습니다.\n\n"
     "겨울 난방비는 문풍지랑 뽁뽁이로 확실히 차이 났습니다."),
]

COMMENT_POOL = [
    "저도 같은 상황이었는데 도움 많이 됐어요.",
    "정보 감사합니다 저장해둘게요!",
    "혹시 지점마다 다르지 않나요?",
    "작년에 해봤는데 서류 한 번에 안 되더라구요.",
    "이거 조건 올해 바뀐 걸로 알고 있어요.",
    "축하드립니다 🎉",
    "저는 반대로 거절당했었는데 부럽네요.",
    "링크도 같이 올려주시면 좋을 것 같아요.",
    "정확히 제가 궁금하던 내용이었습니다.",
    "경험상 미리 전화 문의하는 게 빠릅니다.",
    "지역마다 예산 소진 시점이 달라요.",
    "저도 이번에 신청해보려구요.",
]


def env(key: str, default: str) -> str:
    return os.getenv(key, default)


def connect() -> psycopg.Connection:
    return psycopg.connect(
        host=env("POSTGRES_HOST", "localhost"),
        port=int(env("POSTGRES_PORT", "5432")),
        dbname=env("POSTGRES_DB", "zipsa"),
        user=env("POSTGRES_USER", "zipsa"),
        password=env("POSTGRES_PASSWORD", "changeme"),
    )


def clean(conn: psycopg.Connection) -> None:
    """시드 데이터만 지웁니다. 실제 가입한 계정(demo_user 등)은 건드리지 않습니다."""
    with conn.cursor() as cur:
        cur.execute(f"CREATE TEMP TABLE _su AS SELECT id FROM users WHERE login_id LIKE '{LOGIN_PREFIX}%'")
        cur.execute(f"CREATE TEMP TABLE _sp AS SELECT id FROM policies WHERE external_id LIKE '{EXT_PREFIX}%'")
        for sql in [
            "DELETE FROM post_likes  WHERE user_id IN (SELECT id FROM _su)",
            "DELETE FROM comments    WHERE user_id IN (SELECT id FROM _su)",
            "DELETE FROM comments    WHERE post_id IN (SELECT id FROM posts WHERE user_id IN (SELECT id FROM _su))",
            "DELETE FROM post_likes  WHERE post_id IN (SELECT id FROM posts WHERE user_id IN (SELECT id FROM _su))",
            "DELETE FROM posts       WHERE user_id IN (SELECT id FROM _su)",
            "DELETE FROM policy_views     WHERE user_id IN (SELECT id FROM _su) OR policy_id IN (SELECT id FROM _sp)",
            "DELETE FROM policy_bookmarks WHERE user_id IN (SELECT id FROM _su) OR policy_id IN (SELECT id FROM _sp)",
            "DELETE FROM loan_estimates   WHERE user_id IN (SELECT id FROM _su) OR policy_id IN (SELECT id FROM _sp)",
            "DELETE FROM loan_actuals     WHERE user_id IN (SELECT id FROM _su) OR policy_id IN (SELECT id FROM _sp)",
            "DELETE FROM refresh_tokens   WHERE user_id IN (SELECT id FROM _su)",
            "DELETE FROM policy_ai_summaries WHERE policy_id IN (SELECT id FROM _sp)",
            "DELETE FROM policies WHERE id IN (SELECT id FROM _sp)",
            "DELETE FROM users    WHERE id IN (SELECT id FROM _su)",
        ]:
            cur.execute(sql)
        cur.execute("DROP TABLE _su; DROP TABLE _sp")
    conn.commit()
    print("  기존 시드 데이터 삭제 완료")


def seed(conn: psycopg.Connection) -> None:
    rnd = random.Random(SEED)
    today = date(2026, 9, 3)
    now = datetime(2026, 9, 3, 12, 0, 0)

    with conn.cursor() as cur:
        # ── 1. 정책 ────────────────────────────────
        # 크롤러가 넣은 실제 정책이 있으면 그걸 씁니다.
        # 더미 정책을 덮어씌우면 화면에 가짜 정책이 섞여 나옵니다.
        cur.execute("SELECT id, category FROM policies WHERE external_id NOT LIKE %s",
                    (EXT_PREFIX + "%",))
        real = cur.fetchall()
        policy_ids: dict[str, list[int]] = {}
        if real:
            # 실제 정책은 태그가 없으므로 카테고리를 군집 선호도 태그로 매핑합니다.
            cat_to_tag = {"HOUSING": "rent", "LOAN": "loan",
                          "PUBLIC_HOUSING": "public", "SUPPLY": "newlywed"}
            for pid, cat in real:
                policy_ids.setdefault(cat_to_tag.get(cat, "rent"), []).append(pid)
            for tag in ("rent", "loan", "public", "newlywed", "startup", "jobseek"):
                policy_ids.setdefault(tag, policy_ids.get("rent") or [r[0] for r in real])
            print(f"  실제 정책 {len(real)}건 재사용 (더미 정책 생성 안 함)")

        for i, (tag, title, category, region, issuer) in enumerate([] if real else POLICIES, start=1):
            ext = f"{EXT_PREFIX}{i:03d}"
            start = today - timedelta(days=rnd.randint(10, 120))
            end = today + timedelta(days=rnd.randint(5, 200))
            cur.execute(
                """INSERT INTO policies
                   (external_id, title, content, category, region, issuer,
                    target_job, target_age_range, target_salary_range,
                    apply_start_date, apply_end_date, apply_method,
                    source_name, source_url, crawled_at)
                   VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
                   RETURNING id""",
                (
                    ext, title,
                    f"[개발용 더미] {title} 안내입니다. 지원 대상·금액·신청 방법은 "
                    f"{issuer} 공고문을 따릅니다. 실제 내용이 아니므로 시연 외 용도로 쓰지 마세요.",
                    category, region, issuer,
                    "STUDENT,JOB_SEEKER" if tag == "jobseek" else "EMPLOYEE,SELF_EMPLOYED",
                    "AGE_20S_EARLY,AGE_20S_LATE,AGE_30S_EARLY,AGE_30S_LATE",
                    "UNDER_2000,RANGE_2000_3000,RANGE_3000_4000",
                    start, end, "온라인 신청",
                    issuer, f"https://example.invalid/policy/{ext.lower()}", now,
                ),
            )
            policy_ids.setdefault(tag, []).append(cur.fetchone()[0])
        total_policies = len(real) if real else sum(len(v) for v in policy_ids.values())

        # ── 2. 회원 ────────────────────────────────
        used_nick: set[str] = set()
        cur.execute("SELECT nickname FROM users")
        used_nick.update(r[0] for r in cur.fetchall())

        users: list[tuple[int, dict]] = []
        seq = 0
        for cluster in CLUSTERS:
            for _ in range(cluster["n"]):
                seq += 1
                while True:
                    nick = f"{rnd.choice(ADJ)}{rnd.choice(NOUN)}{rnd.randint(1, 999)}"
                    if nick not in used_nick:
                        used_nick.add(nick)
                        break
                created = now - timedelta(days=rnd.randint(1, 300), minutes=rnd.randint(0, 1439))
                cur.execute(
                    """INSERT INTO users
                       (login_id, password, nickname, age_range, marital_status,
                        job, salary_range, status, created_at, updated_at)
                       VALUES (%s,%s,%s,%s,%s,%s,%s,'ACTIVE',%s,%s) RETURNING id""",
                    (
                        f"{LOGIN_PREFIX}{seq:03d}", DUMMY_PASSWORD_HASH, nick,
                        rnd.choice(cluster["age"]), rnd.choice(cluster["marital"]),
                        rnd.choice(cluster["job"]), rnd.choice(cluster["salary"]),
                        created, created,
                    ),
                )
                users.append((cur.fetchone()[0], cluster))

        # ── 3. 정책 조회 로그 + 찜 ──────────────────
        tags = list(policy_ids)
        n_views = n_bookmarks = 0
        for uid, cluster in users:
            weights = [cluster["weights"][t] for t in tags]
            seen: set[int] = set()
            for _ in range(rnd.randint(8, 28)):
                tag = rnd.choices(tags, weights=weights, k=1)[0]
                pid = rnd.choice(policy_ids[tag])
                cur.execute(
                    "INSERT INTO policy_views (user_id, policy_id, viewed_at) VALUES (%s,%s,%s)",
                    (uid, pid, now - timedelta(days=rnd.randint(0, 60), minutes=rnd.randint(0, 1439))),
                )
                n_views += 1
                seen.add(pid)
            for pid in rnd.sample(sorted(seen), k=min(len(seen), rnd.randint(2, 6))):
                cur.execute(
                    """INSERT INTO policy_bookmarks (user_id, policy_id, created_at)
                       VALUES (%s,%s,%s) ON CONFLICT DO NOTHING""",
                    (uid, pid, now - timedelta(days=rnd.randint(0, 60))),
                )
                n_bookmarks += 1

        # ── 4. 커뮤니티 ────────────────────────────
        uid_list = [u for u, _ in users]
        post_ids: list[int] = []
        for k in range(72):
            category, base, body = POST_TITLES[k % len(POST_TITLES)]
            title = base if k < len(POST_TITLES) else f"{base} ({k // len(POST_TITLES) + 1})"
            created = now - timedelta(days=rnd.randint(0, 90), minutes=rnd.randint(0, 1439))
            cur.execute(
                """INSERT INTO posts (user_id, title, content, category, view_count,
                                      status, created_at, updated_at)
                   VALUES (%s,%s,%s,%s,%s,'PUBLISHED',%s,%s) RETURNING id""",
                (
                    rnd.choice(uid_list), title, body,
                    category, rnd.randint(15, 1200), created, created,
                ),
            )
            post_ids.append(cur.fetchone()[0])

        n_comments = n_likes = 0
        for pid in post_ids:
            for _ in range(rnd.randint(0, 7)):
                cur.execute(
                    """INSERT INTO comments (post_id, user_id, content, status, created_at, updated_at)
                       VALUES (%s,%s,%s,'PUBLISHED',%s,%s)""",
                    (pid, rnd.choice(uid_list), rnd.choice(COMMENT_POOL),
                     now - timedelta(days=rnd.randint(0, 80)), now),
                )
                n_comments += 1
            for liker in rnd.sample(uid_list, k=rnd.randint(0, 25)):
                cur.execute(
                    """INSERT INTO post_likes (post_id, user_id, created_at)
                       VALUES (%s,%s,%s) ON CONFLICT DO NOTHING""",
                    (pid, liker, now - timedelta(days=rnd.randint(0, 80))),
                )
                n_likes += 1

        # 집계 컬럼을 실제 행 수와 맞춥니다(화면에 보이는 숫자와 DB 가 어긋나면 디버깅이 지옥이라서).
        cur.execute("""
            UPDATE posts p SET
              comment_count = (SELECT count(*) FROM comments   c WHERE c.post_id = p.id AND c.status='PUBLISHED'),
              like_count    = (SELECT count(*) FROM post_likes l WHERE l.post_id = p.id)
            WHERE p.id = ANY(%s)""", (post_ids,))

    conn.commit()
    print(f"  정책          {total_policies:>5} 건")
    print(f"  회원          {len(users):>5} 명  ({' / '.join(c['key'] + ':' + str(c['n']) for c in CLUSTERS)})")
    print(f"  정책 조회로그 {n_views:>5} 건")
    print(f"  정책 찜       {n_bookmarks:>5} 건")
    print(f"  게시글        {len(post_ids):>5} 건")
    print(f"  댓글          {n_comments:>5} 건")
    print(f"  좋아요        {n_likes:>5} 건")


def main() -> int:
    ap = argparse.ArgumentParser(description="ZipSa 개발용 더미 데이터")
    ap.add_argument("--reset", action="store_true", help="기존 시드 삭제 후 재생성")
    ap.add_argument("--clean", action="store_true", help="삭제만 하고 종료")
    args = ap.parse_args()

    with connect() as conn:
        if args.reset or args.clean:
            clean(conn)
        if args.clean:
            return 0
        with conn.cursor() as cur:
            cur.execute(f"SELECT count(*) FROM users WHERE login_id LIKE '{LOGIN_PREFIX}%'")
            if cur.fetchone()[0]:
                print("이미 시드 데이터가 있습니다. 다시 만들려면 --reset 을 주세요.", file=sys.stderr)
                return 1
        print("더미 데이터 생성 중...")
        seed(conn)
    print("\n완료. 더미 계정 로그인: seed_user_001 ~ / 비밀번호 Demo!pass12")
    return 0


if __name__ == "__main__":
    sys.exit(main())

# ────────────────────────────────────────────────────────────
#  EXPECTED — 추천 로직이 되찾아내야 하는 패턴
#
#    군집 A (20대초·학생/취준생) → rent, jobseek 태그 정책 상위
#    군집 B (20대후·직장인)      → loan 태그 정책 상위 (중기청·버팀목)
#    군집 C (30대·기혼)          → newlywed 태그 정책 상위
#    군집 D (30대·자영업)        → startup 태그 정책 상위
#
#  추천 결과가 이 표와 다르면 데이터가 아니라 로직을 의심하세요.
#  검증 쿼리는 scripts/verify_seed.sql 참고.
# ────────────────────────────────────────────────────────────
