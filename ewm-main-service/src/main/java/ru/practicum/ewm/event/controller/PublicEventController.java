package ru.practicum.ewm.event.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.event.dto.EventFullDto;
import ru.practicum.ewm.event.dto.EventShortDto;
import ru.practicum.ewm.event.service.EventService;
import ru.practicum.ewm.exception.ValidationException;
import ru.practicum.stats.client.StatsClient;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@Slf4j
public class PublicEventController {

    private static final String APP_NAME = "ewm-main-service";

    private final EventService eventService;
    private final StatsClient statsClient;

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

        log.info("Публичный поиск событий");

        if (rangeStart != null && rangeEnd != null && !rangeEnd.isAfter(rangeStart)) {
            throw new ValidationException("rangeEnd must be after rangeStart");
        }

        String ip = request.getRemoteAddr();
        String uri = request.getRequestURI();

        hitStats(uri, ip);

        return eventService.getPublishedEvents(text, categories, paid, rangeStart, rangeEnd,
                onlyAvailable, sort, from, size, ip);
    }

    @GetMapping("/{id}")
    public EventFullDto getEventById(@PathVariable Long id, HttpServletRequest request) {
        log.info("Публичный запрос события {}", id);

        String ip = request.getRemoteAddr();
        hitStats(request.getRequestURI(), ip);

        return eventService.getPublishedEventById(id, ip);
    }

    private void hitStats(String uri, String ip) {
        try {
            statsClient.hit(APP_NAME, uri, ip, LocalDateTime.now());
        } catch (Exception e) {
            log.warn("Не удалось отправить hit в stats-service: {}", e.getMessage());
        }
    }
}