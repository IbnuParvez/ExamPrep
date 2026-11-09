# ---- Build stage -----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies are cached separately from application sources.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- Runtime stage ---------------------------------------------------------
FROM eclipse-temurin:21-jre

RUN groupadd --system examprep && useradd --system --gid examprep --home /app examprep

WORKDIR /app
COPY --from=build /build/target/examprep-1.0.0.jar /app/app.jar
RUN mkdir -p /app/data/uploads && chown -R examprep:examprep /app

ENV APP_STORAGE_LOCATION=/app/data/uploads \
    SERVER_PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

USER examprep
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
