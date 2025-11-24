# Dockerfile — в корне проекта
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app
COPY . .

# Собираем весь проект (ewm-stats-service)
# Используем mvnw, если он есть, или установленный mvn
# Флаг -Pcoverage может быть лишним, если он не нужен для сборки.
RUN if [ -f mvnw ]; then \
        chmod +x mvnw && ./mvnw clean package -DskipTests; \
    else \
        apt-get update && apt-get install -y maven && \
        mvn clean package -DskipTests; \
    fi

# Финальный образ
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Копируем JAR, который теперь будет исполняемым
COPY --from=builder \
    /app/ewm-stats-service/stats-server/target/stats-server-0.0.1-SNAPSHOT.jar \
    /app/app.jar

EXPOSE 9090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]