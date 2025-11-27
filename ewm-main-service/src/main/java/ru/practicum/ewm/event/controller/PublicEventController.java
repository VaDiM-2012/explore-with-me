package ru.practicum.ewm.event.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.event.dto.EventFullDto;
import ru.practicum.ewm.event.dto.EventShortDto;
import ru.practicum.ewm.event.service.PublicEventService;
import ru.practicum.ewm.exception.ValidationException;
import ru.practicum.stats.client.StatsClient;
import java.time.LocalDateTime;

import java.util.List;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class PublicEventController {

    private static final String APP_NAME = "ewm-main-service";

    private final PublicEventService publicEventService;
    private final StatsClient statsClient;

    private static final Logger log = LoggerFactory.getLogger(PublicEventController.class);

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

        if (rangeStart != null && rangeEnd != null && !rangeEnd.isAfter(rangeStart)) {
            throw new ValidationException("Invalid date range: rangeEnd must be after rangeStart");
        }

        String ip = request.getRemoteAddr();

        // Отправляем информацию о "хите" в сервис статистики
        try {
            statsClient.hit(
                    APP_NAME,
                    request.getRequestURI(),    // Получаем URI запроса
                    ip,                         // IP-адрес клиента
                    LocalDateTime.now()         // Текущее время
            );
        } catch (Exception e) {
            log.warn("Failed to send hit to stats service: {}", e.getMessage());
        }

        return publicEventService.getEvents(text, categories, paid, rangeStart, rangeEnd, onlyAvailable, sort, from, size, ip);
    }

    @GetMapping("/{id}")
    public EventFullDto getEventById(@PathVariable Long id, HttpServletRequest request) {
        String ip = request.getRemoteAddr();

        // Отправляем информацию о "хите" в сервис статистики
        try {
            statsClient.hit(
                    APP_NAME,
                    request.getRequestURI(),    // Получаем URI запроса (включая id)
                    ip,                         // IP-адрес клиента
                    LocalDateTime.now()         // Текущее время
            );
        } catch (Exception e) {
            log.warn("Failed to send hit to stats service: {}", e.getMessage());
        }

        return publicEventService.getEventById(id, ip);
    }
}