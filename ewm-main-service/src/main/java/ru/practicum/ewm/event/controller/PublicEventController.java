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

        log.info("Получен публичный запрос GET /events — поиск событий с параметрами: " +
                "text='{}', categories={}, paid={}, rangeStart={}, rangeEnd={}, onlyAvailable={}, sort={}, from={}, size={}",
                text, categories, paid, rangeStart, rangeEnd, onlyAvailable, sort, from, size);

        if (rangeStart != null && rangeEnd != null && !rangeEnd.isAfter(rangeStart)) {
            log.warn("Некорректный диапазон дат: rangeEnd={} не после rangeStart={}", rangeEnd, rangeStart);
            throw new ValidationException("Invalid date range: rangeEnd must be after rangeStart");
        }

        String ip = request.getRemoteAddr();

        try {
            statsClient.hit(APP_NAME, request.getRequestURI(), ip, LocalDateTime.now());
            log.info("Отправлена статистика о просмотре: URI='{}', IP='{}'", request.getRequestURI(), ip);
        } catch (Exception e) {
            log.warn("Не удалось отправить данные в сервис статистики: {}", e.getMessage());
        }

        List<EventShortDto> events = publicEventService.getEvents(text, categories, paid, rangeStart, rangeEnd, onlyAvailable, sort, from, size, ip);
        log.info("Найдено {} событий по запросу", events.size());
        return events;
    }

    @GetMapping("/{id}")
    public EventFullDto getEventById(@PathVariable Long id, HttpServletRequest request) {
        log.info("Получен публичный запрос GET /events/{} — получение полной информации о событии", id);

        String ip = request.getRemoteAddr();

        try {
            statsClient.hit(APP_NAME, request.getRequestURI(), ip, LocalDateTime.now());
            log.info("Отправлена статистика о просмотре события: event_id={}, IP='{}'", id, ip);
        } catch (Exception e) {
            log.warn("Не удалось отправить данные в сервис статистики: {}", e.getMessage());
        }

        EventFullDto event = publicEventService.getEventById(id, ip);
        log.info("Событие найдено: ID={}, title='{}', initiatorId={}", event.getId(), event.getTitle(), event.getInitiator().getId());
        return event;
    }
}