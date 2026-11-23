# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /build

# Maven dependencies are cached independently of source changes.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline
COPY src ./src
# .dockerignore excludes local application.yml, which can contain credentials.
COPY docker/application.yml ./src/main/resources/application.yml
COPY docker/PasswordHashTool.java ./src/main/java/com/lab/utils/PasswordHashTool.java
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests clean package

FROM eclipse-temurin:8-jre-jammy AS runtime
ENV TZ=Asia/Shanghai
WORKDIR /app
# Temurin already includes curl, CA certificates and timezone data.
RUN groupadd --gid 10001 lab \
    && useradd --uid 10001 --gid lab --create-home --shell /usr/sbin/nologin lab \
    && mkdir -p /app/uploads \
    && chown lab:lab /app/uploads
COPY --from=build --chown=lab:lab /build/target/lab-backend.jar /app/app.jar
USER lab
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
    CMD curl --fail --silent --show-error http://127.0.0.1:8080/api/health || exit 1
ENTRYPOINT ["java", "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8", "-Duser.timezone=Asia/Shanghai", "-jar", "/app/app.jar"]
