package ru.practicum.ewm.event.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.event.dto.EventFullDto;
import ru.practicum.ewm.event.dto.EventShortDto;
import ru.practicum.ewm.event.mapper.EventMapper;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.State;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.stats.client.StatsClient;
import ru.practicum.stats.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicEventServiceImpl implements PublicEventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final StatsClient statsClient;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final LocalDateTime DISTANT_PAST = LocalDateTime.of(1970, 1, 1, 0, 0, 0);

    @Override
    public List<EventShortDto> getEvents(String text,
                                         List<Long> categories,
                                         Boolean paid,
                                         LocalDateTime rangeStart,
                                         LocalDateTime rangeEnd,
                                         Boolean onlyAvailable,
                                         String sort,
                                         Integer from,
                                         Integer size,
                                         String ip) {

        statsClient.hit("ewm-main-service", "/events", ip, LocalDateTime.now());

        LocalDateTime start = (rangeStart == null) ? LocalDateTime.now() : rangeStart;
        LocalDateTime end = (rangeEnd == null) ? LocalDateTime.now().plusYears(100) : rangeEnd;

        Sort sorting = (sort != null && sort.equals("EVENT_DATE")) ?
                Sort.by("eventDate").ascending() : Sort.unsorted();

        PageRequest page = PageRequest.of(from / size, size, sorting);

        List<Event> events = eventRepository.findPublicEvents(text, categories, paid, start, end, page);

        if (onlyAvailable != null && onlyAvailable) {
            events = events.stream()
                    .filter(e -> e.getParticipantLimit() == 0 || getConfirmedRequests(e.getId()) < e.getParticipantLimit())
                    .toList();
        }

        Map<Long, Long> viewsMap = getViewsForEvents(events);

        List<EventShortDto> dtos = events.stream()
                .map(eventMapper::toShortDto)
                .peek(dto -> dto.setViews(viewsMap.getOrDefault(dto.getId(), 0L)))
                .toList();

        if (sort != null && sort.equals("VIEWS")) {
            dtos = dtos.stream()
                    .sorted(Comparator.comparing(EventShortDto::getViews).reversed())
                    .skip(from)
                    .limit(size)
                    .collect(Collectors.toList());
        }

        return dtos;
    }

    @Override
    public EventFullDto getEventById(Long id, String ip) {
        statsClient.hit("ewm-main-service", "/events/" + id, ip, LocalDateTime.now());

        Event event = eventRepository.findByIdAndState(id, State.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Event with id=" + id + " not found or not published"));

        Map<Long, Long> viewsMap = getViewsForEvents(List.of(event));

        EventFullDto dto = eventMapper.toFullDto(event);
        dto.setViews(viewsMap.getOrDefault(id, 0L));

        return dto;
    }

    private Map<Long, Long> getViewsForEvents(List<Event> events) {
        if (events.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> uris = events.stream()
                .map(e -> "/events/" + e.getId())
                .collect(Collectors.toList());

        LocalDateTime earliest = events.stream()
                .map(Event::getPublishedOn)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(DISTANT_PAST);

        List<ViewStatsDto> stats = statsClient.getStats(earliest, LocalDateTime.now(), uris, true);

        return stats.stream()
                .collect(Collectors.toMap(
                        s -> Long.parseLong(s.getUri().split("/")[2]),
                        ViewStatsDto::getHits
                ));
    }

    // Stub for confirmedRequests, replace with actual if implemented
    private long getConfirmedRequests(Long eventId) {
        return 0L; // Assume no requests or implement with RequestRepository
    }
}