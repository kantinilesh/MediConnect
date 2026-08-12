# ─────────────────────────────────────────────────────────────
# Stage 1 — Build
# ─────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /workspace

# Cache dependency layer first
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B 2>/dev/null || true

COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -B

# ─────────────────────────────────────────────────────────────
# Stage 2 — Runtime
# ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy AS runtime

LABEL maintainer="kantinilesh2312@gmail.com"
LABEL project="mediconnect"

# Non-root user for security
RUN groupadd -r mediconnect && useradd -r -g mediconnect mediconnect

WORKDIR /app

COPY --from=builder /workspace/target/mediconnect-*.jar app.jar

RUN chown mediconnect:mediconnect app.jar

USER mediconnect

EXPOSE 8080

# Health-check so docker-compose knows when the app is ready
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-Xms256m", "-Xmx512m", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
