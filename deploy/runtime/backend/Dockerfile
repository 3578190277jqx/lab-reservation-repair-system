FROM eclipse-temurin:8-jre-jammy
ENV TZ=Asia/Shanghai
WORKDIR /app
RUN groupadd --gid 10001 lab && useradd --uid 10001 --gid lab --create-home --shell /usr/sbin/nologin lab && mkdir -p /app/uploads && chown lab:lab /app/uploads
COPY --chown=lab:lab app.jar /app/app.jar
USER lab
EXPOSE 8080
ENTRYPOINT ["java","-Djava.awt.headless=true","-Dfile.encoding=UTF-8","-Duser.timezone=Asia/Shanghai","-jar","/app/app.jar"]
