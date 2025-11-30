package ru.practicum.ewm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "ru.practicum.ewm",      // компоненты основного сервиса
        "ru.practicum.stats"     // компоненты клиента статистики
})
public class EwmMainApplication {
    public static void main(String[] args) {
        SpringApplication.run(EwmMainApplication.class, args);
    }
}