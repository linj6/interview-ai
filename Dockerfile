FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY docker/maven/settings.xml /opt/maven-settings.xml
COPY pom.xml ./
COPY interview-agent-bootstrap ./interview-agent-bootstrap
COPY interview-agent-common ./interview-agent-common
COPY interview-agent-api ./interview-agent-api
COPY interview-agent-user ./interview-agent-user
COPY interview-agent-candidate ./interview-agent-candidate
COPY interview-agent-position ./interview-agent-position
COPY interview-agent-interview ./interview-agent-interview
COPY interview-agent-evaluation ./interview-agent-evaluation
COPY interview-agent-report ./interview-agent-report
COPY interview-agent-ai ./interview-agent-ai
# Windows checkouts may use CRLF; normalize the container copy for Linux Spotless.
RUN find . -type f \( -name '*.java' -o -name '*.xml' \) -exec sed -i 's/\r$//' {} + \
    && mvn --settings /opt/maven-settings.xml --batch-mode --no-transfer-progress -pl interview-agent-bootstrap -am package -DskipTests \
    && cp interview-agent-bootstrap/target/interview-agent-bootstrap-*.jar /app.jar

FROM eclipse-temurin:17-jre-noble
# Compose healthcheck requires curl; declare it independently of the base image.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build --chown=app:app /app.jar ./app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Asia/Shanghai", "-jar", "/app/app.jar"]
