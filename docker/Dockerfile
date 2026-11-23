# syntax=docker/dockerfile:1

# ==========================================
# Stage 1: Maven Build
# ==========================================

FROM eclipse-temurin:21-jdk-noble AS builder

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x mvnw

RUN ./mvnw -B dependency:go-offline

COPY src/ src/

RUN ./mvnw -B clean package -DskipTests

# ==========================================
# Stage 2: Runtime
# ==========================================

FROM eclipse-temurin:21-jre-noble AS runtime

WORKDIR /app

RUN groupadd --gid 10001 appgroup \
    && useradd \
        --uid 10001 \
        --gid 10001 \
        --no-create-home \
        --shell /usr/sbin/nologin \
        appuser

COPY --from=builder --chown=10001:10001 \
    /app/target/*.jar /app/app.jar

USER 10001:10001

EXPOSE 8080 9090

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/urandom"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]