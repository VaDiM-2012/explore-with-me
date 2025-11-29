package ru.practicum.ewm.event.service;

import ru.practicum.ewm.event.dto.*;

import java.time.LocalDateTime;
import java.util.List;

public interface EventService {

    List<EventFullDto> searchEventsForAdmin(List<Long> users,
                                            List<String> states,
                                            List<Long> categories,
                                            LocalDateTime rangeStart,
                                            LocalDateTime rangeEnd,
                                            Integer from,
                                            Integer size);

    EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest request);

    EventFullDto createEventByInitiator(Long userId, NewEventDto dto);

    List<EventShortDto> getEventsByInitiator(Long userId, Integer from, Integer size);

    EventFullDto getEventByInitiator(Long userId, Long eventId);

    EventFullDto updateEventByInitiator(Long userId, Long eventId, UpdateEventUserRequest request);

    List<EventShortDto> getPublishedEvents(String text,
                                           List<Long> categories,
                                           Boolean paid,
                                           LocalDateTime rangeStart,
                                           LocalDateTime rangeEnd,
                                           Boolean onlyAvailable,
                                           String sort,
                                           Integer from,
                                           Integer size,
                                           String ip);

    EventFullDto getPublishedEventById(Long id, String ip);
}