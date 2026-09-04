# 1단계: 빌드
FROM node:22-alpine AS build
WORKDIR /app

# package*.json 만 먼저 복사해야 소스 변경 시 npm ci 캐시가 유지된다
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# VITE_* 는 런타임이 아니라 "빌드 시점"에 번들에 박힌다.
# compose 의 build.args 로 넘겨받지 않으면 컨테이너에 env 를 줘도 undefined 가 된다.
ARG VITE_KAKAO_MAP_KEY=""
ARG VITE_API_BASE_URL=""
ENV VITE_KAKAO_MAP_KEY=$VITE_KAKAO_MAP_KEY
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

# 이미지 안에는 리포 루트 .env 가 없으므로 envDir 가 비어도 위 ENV 로 주입된다
RUN npm run build

# 2단계: 정적 파일 서빙 + /api 프록시
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
