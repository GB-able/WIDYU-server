# ---------- Build stage ----------
FROM amazoncorretto:21-alpine AS build
WORKDIR /workspace
COPY . .
RUN gradle :backend:widyu-api:clean :backend:widyu-api:bootJar --no-daemon

# ---------- Run stage ----------
FROM amazoncorretto:21-alpine
WORKDIR /app
# 멀티모듈 구조에 맞게 경로 지정
COPY --from=build /workspace/backend/widyu-api/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
