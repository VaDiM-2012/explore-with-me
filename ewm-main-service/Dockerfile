# ===== Этап сборки =====
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

# Копируем всё из контекста (корень проекта)
COPY . .

# Делаем mvnw исполняемым (на всякий случай) и собираем только нужный модуль
RUN chmod +x mvnw && \
    ./mvnw clean package -DskipTests -pl ewm-main-service -am

# ===== Финальный образ =====
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Копируем только получившийся JAR (с wildcard — на случай другой версии)
COPY --from=builder /app/ewm-main-service/target/ewm-main-service-*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]