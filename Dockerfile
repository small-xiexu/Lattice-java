FROM eclipse-temurin:21-jre

WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=local-dev \
    SERVER_PORT=18082

ARG JAR_FILE=target/lattice-java-1.0-SNAPSHOT.jar

COPY ${JAR_FILE} /app/app.jar

EXPOSE 18082

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
