FROM node:20-alpine AS frontend-build
WORKDIR /workspace/frontend

COPY frontend/package*.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

FROM node:20-alpine AS mobile-web-build
WORKDIR /workspace/mobile

COPY mobile/package*.json ./
RUN npm ci --no-audit --no-fund

COPY mobile/ ./
# Expo 웹 포트폴리오 화면을 Spring의 /mobile 정적 리소스로 내보낸다.
RUN npm run build:web

FROM eclipse-temurin:21-jdk-alpine AS backend-build
WORKDIR /workspace

COPY gradlew gradlew.bat build.gradle settings.gradle ./
COPY gradle/ ./gradle/
RUN chmod +x ./gradlew

COPY src/ ./src/
COPY --from=frontend-build /workspace/frontend/dist/ ./src/main/resources/static/
COPY --from=mobile-web-build /workspace/mobile/dist/ ./src/main/resources/static/mobile/
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=backend-build /workspace/build/libs/*.jar ./app.jar

USER app
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:-} -jar /app/app.jar"]
