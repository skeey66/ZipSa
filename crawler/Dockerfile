FROM python:3.11-slim
WORKDIR /app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY zipsa_crawler ./zipsa_crawler

RUN useradd --system --create-home --shell /usr/sbin/nologin zipsa
USER zipsa

# 배치입니다. 상주 서비스가 아니라 필요할 때 한 번씩 실행합니다.
#   docker compose run --rm crawler --target policy
ENTRYPOINT ["python", "-m", "zipsa_crawler.main"]
