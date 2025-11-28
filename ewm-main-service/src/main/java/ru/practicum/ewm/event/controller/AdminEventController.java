package ru.practicum.ewm.event.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.event.dto.EventFullDto;
import ru.practicum.ewm.event.dto.UpdateEventAdminRequest;
import ru.practicum.ewm.event.service.AdminEventService;
import ru.practicum.ewm.exception.ValidationException;

import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/admin/events")
@RequiredArgsConstructor
@Slf4j
public class AdminEventController {

    private final AdminEventService adminEventService;

    @GetMapping
    public List<EventFullDto> searchEvents(
            @RequestParam(required = false) List<Long> users,
            @RequestParam(required = false) List<String> states,
            @RequestParam(required = false) List<Long> categories,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime rangeStart,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime rangeEnd,
            @RequestParam(defaultValue = "0") Integer from,
            @RequestParam(defaultValue = "10") Integer size) {

        log.info("Получен запрос GET /admin/events — поиск событий с параметрами: " +
                "users={}, states={}, categories={}, rangeStart={}, rangeEnd={}, from={}, size={}",
                users, states, categories, rangeStart, rangeEnd, from, size);

        if (rangeStart != null && rangeEnd != null && !rangeEnd.isAfter(rangeStart)) {
            log.warn("Некорректный диапазон дат: rangeEnd={} не после rangeStart={}", rangeEnd, rangeStart);
            throw new ValidationException("Invalid date range: rangeEnd must be after rangeStart");
        }

        List<EventFullDto> events = adminEventService.searchEvents(users, states, categories, rangeStart, rangeEnd, from, size);
        log.info("Найдено {} событий по заданным критериям", events.size());
        return events;
    }


    @PatchMapping("/{eventId}")
    public EventFullDto updateEventByAdmin(@PathVariable Long eventId,
                                           @Valid @RequestBody UpdateEventAdminRequest request) {
        log.info("Получен запрос PATCH /admin/events/{} — обновление события администратором: {}", eventId, request);
        EventFullDto updatedEvent = adminEventService.updateEventByAdmin(eventId, request);
        log.info("Событие с ID {} успешно обновлено администратором", updatedEvent.getId());
        return updatedEvent;
    }
}