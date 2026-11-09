# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build

# Dependencies are resolved from the lock-step pom before sources are copied, so
# application edits do not invalidate the dependency layer.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system --gid 1001 examprep \
    && useradd --system --uid 1001 --gid examprep --home-dir /app --shell /usr/sbin/nologin examprep

WORKDIR /app
COPY --from=build /build/target/examprep-1.0.0.jar /app/app.jar

RUN mkdir -p /app/data/uploads && chown -R examprep:examprep /app

USER examprep
EXPOSE 8080

ENV APP_STORAGE_LOCATION=/app/data/uploads \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
