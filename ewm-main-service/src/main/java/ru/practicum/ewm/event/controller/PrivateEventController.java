package ru.practicum.ewm.event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.event.dto.EventFullDto;
import ru.practicum.ewm.event.dto.EventShortDto;
import ru.practicum.ewm.event.dto.NewEventDto;
import ru.practicum.ewm.event.dto.UpdateEventUserRequest;
import ru.practicum.ewm.event.service.PrivateEventService;
import ru.practicum.ewm.request.dto.EventRequestStatusUpdateRequest;
import ru.practicum.ewm.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.ewm.request.dto.ParticipationRequestDto;
import ru.practicum.ewm.request.service.RequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/users/{userId}/events")
@RequiredArgsConstructor
@Validated
public class PrivateEventController {

    private static final Logger log = LoggerFactory.getLogger(PrivateEventController.class);

    private final PrivateEventService service;
    private final RequestService requestService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventFullDto createEvent(@PathVariable Long userId,
                                    @Valid @RequestBody NewEventDto dto) {
        log.info("Получен запрос POST /users/{}/events — создание события пользователем: {}", userId, dto);
        EventFullDto result = service.createEvent(userId, dto);
        log.info("Событие успешно создано с ID: {}", result.getId());
        return result;
    }

    @GetMapping
    public List<EventShortDto> getUserEvents(@PathVariable Long userId,
                                             @RequestParam(defaultValue = "0") Integer from,
                                             @RequestParam(defaultValue = "10") Integer size) {
        log.info("Получен запрос GET /users/{}/events — получение списка событий пользователя: from={}, size={}", userId, from, size);
        List<EventShortDto> events = service.getUserEvents(userId, from, size);
        log.info("Пользователь {} имеет {} событий", userId, events.size());
        return events;
    }

    @GetMapping("/{eventId}")
    public EventFullDto getUserEventById(@PathVariable Long userId,
                                         @PathVariable Long eventId) {
        log.info("Получен запрос GET /users/{}/events/{} — получение полной информации о событии", userId, eventId);
        EventFullDto event = service.getUserEventById(userId, eventId);
        log.info("Событие найдено: ID={}, title='{}'", event.getId(), event.getTitle());
        return event;
    }

    @PatchMapping("/{eventId}")
    public EventFullDto updateEvent(@PathVariable Long userId,
                                    @PathVariable Long eventId,
                                    @Valid @RequestBody UpdateEventUserRequest request) {
        log.info("Получен запрос PATCH /users/{}/events/{} — обновление события пользователем: {}", userId, eventId, request);
        EventFullDto updated = service.updateEvent(userId, eventId, request);
        log.info("Событие с ID {} успешно обновлено", updated.getId());
        return updated;
    }

    @GetMapping("/{eventId}/requests")
    public List<ParticipationRequestDto> getEventRequests(@PathVariable Long userId,
                                                          @PathVariable Long eventId) {
        log.info("Получен запрос GET /users/{}/events/{}/requests — получение заявок на участие в событии", userId, eventId);
        List<ParticipationRequestDto> requests = requestService.getEventRequests(userId, eventId);
        log.info("Найдено {} заявок на участие в событии {}", requests.size(), eventId);
        return requests;
    }

    @PatchMapping("/{eventId}/requests")
    public EventRequestStatusUpdateResult updateEventRequestsStatus(
            @PathVariable Long userId,
            @PathVariable Long eventId,
            @Valid @RequestBody EventRequestStatusUpdateRequest updateRequest) {
        log.info("Получен запрос PATCH /users/{}/events/{}/requests — обновление статусов заявок: {}", userId, eventId, updateRequest);
        EventRequestStatusUpdateResult result = requestService.updateRequestsStatus(userId, eventId, updateRequest);
        log.info("Статусы заявок на событие {} обновлены: подтверждено={}, отклонено={}",
                eventId, result.getConfirmedRequests().size(), result.getRejectedRequests().size());
        return result;
    }
}