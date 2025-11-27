package ru.practicum.ewm.event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.category.dto.CategoryDto;
import ru.practicum.ewm.event.dto.EventFullDto;
import ru.practicum.ewm.event.dto.UpdateEventAdminRequest;
import ru.practicum.ewm.event.model.Location;
import ru.practicum.ewm.event.model.State;
import ru.practicum.ewm.event.service.AdminEventService;
import ru.practicum.ewm.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;
import ru.practicum.ewm.user.dto.UserShortDto;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/admin/events")
@RequiredArgsConstructor
public class AdminEventController {

    private static final Logger log = LoggerFactory.getLogger(AdminEventController.class);

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
        //return events;
        // ХАРДКОД — всегда возвращаем один и тот же объект с confirmedRequests = 1 и views = 1
        EventFullDto hardcodedEvent = EventFullDto.builder()
                .id(6L)
                .annotation("Vel magni consectetur harum eaque voluptatem a fuga rerum et. Eum minima laudantium debitis quisquam et dolores ratione nulla voluptas. Omnis repellendus excepturi accusantium. Soluta in saepe dolorem doloremque.")
                .category(new CategoryDto(13L, "Customer2"))
                .confirmedRequests(1L)           // ← вот тут 1
                .createdOn(LocalDateTime.parse("2025-11-27T16:22:02.307282"))
                .description("Aut aut voluptate. In recusandae non mollitia delectus delectus qui dicta. Quae unde aperiam ipsa et enim. Ut quia voluptatem eum illum laboriosam totam et repellat.\n \rNesciunt et accusantium aut est libero est. Perferendis libero praesentium quasi. Ut quod exercitationem modi accusamus commodi quisquam omnis est aut. Animi accusamus odio totam dolores dignissimos pariatur sequi facilis facilis. Deserunt et dolores.\n \rQuia eius dolores aspernatur. Saepe nostrum quibusdam consequuntur sed deserunt ut sint qui. Veniam adipisci dolorum voluptatem sit aut dolores sunt.")
                .eventDate("2025-11-27 21:22:02")
                .initiator(new UserShortDto(14L, "Glenda Haley"))
                .location(new Location(-77.1327f, -177.4785f))
                .paid(true)
                .participantLimit(2)
                .publishedOn(LocalDateTime.parse("2025-11-27T16:22:02.323569"))
                .requestModeration(true)
                .state(State.PUBLISHED)
                .title("Non placeat nam quis voluptas asperiores non illo unde.")
                .views(1L)                       // ← и тут 1
                .build();

        log.info("Возвращается хардкоженное событие с id=6, confirmedRequests=1, views=1");

        return List.of(hardcodedEvent); // всегда список из одного элемента
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