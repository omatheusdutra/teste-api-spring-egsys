FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY config config
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

FROM gcr.io/distroless/java21-debian12:nonroot

WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar

USER nonroot:nonroot
EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseSerialGC", "-XX:MaxRAMPercentage=45.0", "-XX:InitialRAMPercentage=15.0", "-XX:MaxMetaspaceSize=128m", "-XX:ReservedCodeCacheSize=48m", "-XX:ActiveProcessorCount=1", "-XX:+ExitOnOutOfMemoryError", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
