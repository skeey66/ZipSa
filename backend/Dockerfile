# 1단계: 빌드 — 의존성 레이어를 소스와 분리해 캐시가 살아있게 한다
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# 2단계: 실행 — JDK 가 아닌 JRE 로 이미지를 줄인다
FROM eclipse-temurin:21-jre
WORKDIR /app

# healthcheck 에 필요. JRE 이미지에는 curl 이 들어있지 않다.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# root 로 돌리지 않는다
RUN useradd --system --create-home --shell /usr/sbin/nologin zipsa
USER zipsa

COPY --from=build --chown=zipsa:zipsa /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
