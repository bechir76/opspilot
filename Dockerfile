# One Dockerfile for every service:
#   docker build --build-arg SERVICE=order-service -t opspilot/order-service:dev .

# ---- Build stage: full JDK + Maven, discarded after the build ----
FROM maven:3.9-eclipse-temurin-21 AS build
ARG SERVICE
RUN test -n "$SERVICE" || (echo "Build arg SERVICE is required (e.g. order-service)" >&2 && exit 1)
WORKDIR /workspace

COPY pom.xml .
COPY chaos-lib chaos-lib
COPY services services

# The cache mount keeps ~/.m2 between builds, so dependencies are not re-downloaded
# every time a source file changes. -am also builds chaos-lib from source.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl services/${SERVICE} -am package -DskipTests \
 && cp services/${SERVICE}/target/${SERVICE}-*.jar /workspace/app.jar

# ---- Runtime stage: JRE only, non-root ----
FROM eclipse-temurin:21-jre-alpine
ARG SERVICE
ARG REVISION=unknown

LABEL org.opencontainers.image.title="${SERVICE}" \
      org.opencontainers.image.description="OpsPilot demo workload: ${SERVICE}" \
      org.opencontainers.image.source="https://github.com/bechir76/opspilot" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.vendor="bechir76"

RUN addgroup -S -g 10001 app && adduser -S -D -H -u 10001 -G app app

WORKDIR /app
# Owned by root and only readable by the app user: a compromised process cannot rewrite its own binary.
COPY --from=build /workspace/app.jar app.jar

# Heap sized from the container memory limit; a Java OutOfMemoryError kills the process
# so the orchestrator restarts it, instead of leaving a half-broken JVM running.
ENV PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

USER 10001:10001
EXPOSE 8080

# exec: java replaces the shell as PID 1, so it receives SIGTERM and shuts down gracefully.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
