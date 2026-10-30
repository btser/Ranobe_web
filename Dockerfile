FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY src/main ./src/main
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system ranobe && useradd --system --gid ranobe --home-dir /app ranobe \
    && mkdir -p /app/uploads && chown -R ranobe:ranobe /app
COPY --from=build --chown=ranobe:ranobe /build/target/project-web-0.0.1-SNAPSHOT.jar /app/app.jar
USER ranobe
ENV SPRING_PROFILES_ACTIVE=render
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
