package ru.practicum.ewm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"ru.practicum.ewm.main", "ru.practicum.stats.client"})
public class EwmMainApplication {
    public static void main(String[] args) {
        SpringApplication.run(EwmMainApplication.class, args);
    }
}