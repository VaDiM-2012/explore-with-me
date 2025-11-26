package ru.practicum.ewm.event.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.event.dto.EventFullDto;
import ru.practicum.ewm.event.dto.EventShortDto;
import ru.practicum.ewm.event.service.PublicEventService;
import ru.practicum.stats.client.StatsClient; // Импорт клиента статистики
import java.time.LocalDateTime; // Импорт для получения текущего времени

import java.util.List;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class PublicEventController {

    // Константа для имени сервиса при отправке статистики
    private static final String APP_NAME = "ewm-main-service";

    private final PublicEventService publicEventService;
    private final StatsClient statsClient; // Инжекция клиента статистики

    @GetMapping
    public List<EventShortDto> getEvents(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) List<Long> categories,
            @RequestParam(required = false) Boolean paid,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime rangeStart,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime rangeEnd,
            @RequestParam(defaultValue = "false") Boolean onlyAvailable,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") Integer from,
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request) {

        String ip = request.getRemoteAddr();

        // Отправляем информацию о "хите" в сервис статистики
        statsClient.hit(
                APP_NAME,
                request.getRequestURI(),    // Получаем URI запроса
                ip,                         // IP-адрес клиента
                LocalDateTime.now()         // Текущее время
        );

        return publicEventService.getEvents(text, categories, paid, rangeStart, rangeEnd, onlyAvailable, sort, from, size, ip);
    }

    @GetMapping("/{id}")
    public EventFullDto getEventById(@PathVariable Long id, HttpServletRequest request) {
        String ip = request.getRemoteAddr();

        // Отправляем информацию о "хите" в сервис статистики
        statsClient.hit(
                APP_NAME,
                request.getRequestURI(),    // Получаем URI запроса (включая id)
                ip,                         // IP-адрес клиента
                LocalDateTime.now()         // Текущее время
        );

        return publicEventService.getEventById(id, ip);
    }
}