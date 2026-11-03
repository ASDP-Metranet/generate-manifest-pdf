# syntax=docker/dockerfile:1
# Tahap 1: build JAR dengan Java 25 & Maven. Docker akan menjalankan unit test.
FROM maven:3.9-eclipse-temurin-25-noble AS build
WORKDIR /workspace

# Cache dependencies ketika source code berubah tetapi pom.xml tetap.
COPY pom.xml ./
RUN mvn -B -ntp -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -ntp clean verify

# Tahap 2: runtime kecil, hanya Java 25 JRE, tanpa Maven dan source code.
FROM eclipse-temurin:25-jre-noble

# Gunakan user non-root ketika menjalankan Spring Boot.
RUN groupadd --system --gid 10001 appgroup \
    && useradd --system --uid 10001 --gid 10001 \
       --no-create-home --shell /usr/sbin/nologin appuser

WORKDIR /app
COPY --from=build --chown=10001:10001 \
    /workspace/target/manifest-pdf-service-1.1.0.jar /app/app.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -Duser.timezone=Asia/Jakarta -Dfile.encoding=UTF-8"
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
