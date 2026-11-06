FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 10001 machineguard && useradd --system --uid 10001 --gid 10001 --home /tmp --shell /usr/sbin/nologin machineguard
WORKDIR /app
RUN chmod 0555 /app
COPY --chmod=0444 --from=build /workspace/target/core-api-0.1.0-SNAPSHOT.jar /app/core-api.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/core-api.jar"]
