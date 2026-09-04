"""환경변수 로딩. 값은 리포 루트의 .env 에서 읽습니다(커밋 금지)."""

import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

_ROOT = Path(__file__).resolve().parents[2]
load_dotenv(_ROOT / ".env")


@dataclass(frozen=True)
class Settings:
    db_host: str
    db_port: int
    db_name: str
    db_user: str
    db_password: str
    user_agent: str
    delay_seconds: float
    # 외부 API 키. 빈 문자열이면 해당 수집기를 돌릴 수 없습니다.
    data_go_kr_key: str
    youth_center_key: str
    kakao_rest_key: str

    @property
    def dsn(self) -> str:
        return (
            f"host={self.db_host} port={self.db_port} dbname={self.db_name} "
            f"user={self.db_user} password={self.db_password}"
        )


def load_settings() -> Settings:
    return Settings(
        db_host=os.getenv("POSTGRES_HOST", "localhost"),
        db_port=int(os.getenv("POSTGRES_PORT", "5432")),
        db_name=os.getenv("POSTGRES_DB", "zipsa"),
        db_user=os.getenv("POSTGRES_USER", "zipsa"),
        db_password=os.getenv("POSTGRES_PASSWORD", "changeme"),
        user_agent=os.getenv("CRAWL_USER_AGENT", "ZipSaBot/1.0"),
        delay_seconds=float(os.getenv("CRAWL_DELAY_SECONDS", "1.0")),
        data_go_kr_key=os.getenv("DATA_GO_KR_SERVICE_KEY", ""),
        youth_center_key=os.getenv("YOUTH_CENTER_API_KEY", ""),
        kakao_rest_key=os.getenv("KAKAO_REST_API_KEY", ""),
    )


def require(value: str, env_name: str, where: str) -> str:
    """키가 비어 있으면 수집을 시작하기 전에 명확히 실패시킵니다.

    빈 키로 그냥 호출하면 원격 API 가 200 에 빈 배열을 주는 경우가 있어
    "0건 수집 성공" 으로 조용히 넘어가 버립니다. 그게 제일 찾기 어렵습니다.
    """
    if not value.strip():
        raise RuntimeError(
            f"{env_name} 가 비어 있습니다. 리포 루트 .env 에 채우세요. (필요한 곳: {where})"
        )
    return value
