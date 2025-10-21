FROM amazoncorretto:21-alpine
WORKDIR /app
COPY --from=build /workspace/backend/widyu-api/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
