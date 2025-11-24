FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app
COPY . .

RUN if [ -f mvnw ]; then \
        chmod +x mvnw && ./mvnw clean package -DskipTests -pl :stats-server --also-make; \
    else \
        apt-get update && apt-get install -y maven && \
        mvn clean package -DskipTests -pl :stats-server --also-make; \
    fi

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Самый надёжный вариант — копируем jar с точным именем
COPY --from=builder /app/ewm-stats-service/stats-server/target/stats-server-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 9090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]