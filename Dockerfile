# Dockerfile — лежит в корне проекта (рядом с docker-compose.yml)
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app

# Копируем всё
COPY . .

# Если mvnw есть — используем его, если нет — ставим maven через apt
RUN if [ -f mvnw ]; then \
        chmod +x mvnw && ./mvnw clean package -DskipTests -pl :stats-server --also-make; \
    else \
        apt-get update && \
        apt-get install -y maven && \
        mvn clean package -DskipTests -pl :stats-server --also-make; \
    fi

# Финальный образ
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Копируем готовый jar (имя может чуть отличаться, поэтому берём *.jar)
COPY --from=builder /app/ewm-stats-service/stats-server/target/stats-server-*.jar app.jar

EXPOSE 9090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]