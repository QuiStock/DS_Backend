FROM eclipse-temurin:17-jdk-noble AS build

WORKDIR /app

COPY . .

RUN ./gradlew bootJar --no-daemon


FROM eclipse-temurin:17-jre-noble AS runtime

LABEL org.opencontainers.image.title="QuiStock API Core"

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
