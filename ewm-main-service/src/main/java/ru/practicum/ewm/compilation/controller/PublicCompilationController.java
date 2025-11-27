package ru.practicum.ewm.compilation.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.compilation.dto.CompilationDto;
import ru.practicum.ewm.compilation.service.CompilationService;
import ru.practicum.stats.client.StatsClient; // Импорт клиента статистики
import jakarta.servlet.http.HttpServletRequest; // Импорт для доступа к данным запроса

import java.time.LocalDateTime; // Импорт для получения текущего времени

import java.util.List;

@RestController
@RequestMapping("/compilations")
@RequiredArgsConstructor
public class PublicCompilationController {

    // Используем константу для имени сервиса при отправке статистики
    private static final String APP_NAME = "ewm-main-service";

    private final CompilationService service;
    private final StatsClient statsClient; // Инжекция клиента статистики

    @GetMapping
    public List<CompilationDto> getCompilations(
            @RequestParam(required = false) Boolean pinned,
            @RequestParam(defaultValue = "0") Integer from,
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request // Добавление объекта запроса для получения IP и URI
    ) {
        // Отправляем информацию о "хите" в сервис статистики

        statsClient.hit(
                APP_NAME,
                request.getRequestURI(),    // Получаем URI запроса
                request.getRemoteAddr(),    // Получаем IP-адрес клиента
                LocalDateTime.now()         // Текущее время
        );


        return service.getCompilations(pinned, from, size);
    }

    @GetMapping("/{compId}")
    public CompilationDto getCompilationById(
            @PathVariable Long compId,
            HttpServletRequest request // Добавление объекта запроса для получения IP и URI
    ) {
        // Отправляем информацию о "хите" в сервис статистики

        statsClient.hit(
                APP_NAME,
                request.getRequestURI(),    // Получаем URI запроса (включая compId)
                request.getRemoteAddr(),    // Получаем IP-адрес клиента
                LocalDateTime.now()         // Текущее время
        );


        return service.getCompilationById(compId);
    }
}