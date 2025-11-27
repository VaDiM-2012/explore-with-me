package ru.practicum.ewm.event.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import ru.practicum.ewm.request.model.RequestStatus;
import ru.practicum.ewm.request.repository.RequestRepository;
import ru.practicum.stats.client.StatsClient;
import ru.practicum.stats.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicEventServiceImpl implements PublicEventService {

    private final EventRepository eventRepository;
    private final RequestRepository requestRepository;
    private final EventMapper eventMapper;
    private final StatsClient statsClient;

    private static final Logger log = LoggerFactory.getLogger(PublicEventServiceImpl.class);

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final LocalDateTime DISTANT_PAST = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
    private static final Pattern EVENT_URI_PATTERN = Pattern.compile("^/events/(\\d+)$");

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

        // Отправляем информацию о "хите" в сервис статистики (дубликат из контроллера, но оставляем)
        try {
            statsClient.hit("ewm-main-service", "/events", ip, LocalDateTime.now());
        } catch (Exception e) {
            log.warn("Failed to send hit to stats service: {}", e.getMessage());
        }

        LocalDateTime start = rangeStart != null ? rangeStart : LocalDateTime.now();
        LocalDateTime end = rangeEnd != null ? rangeEnd : LocalDateTime.now().plusYears(100);

        Sort sorting = "EVENT_DATE".equalsIgnoreCase(sort)
                ? Sort.by("eventDate").ascending()
                : Sort.unsorted();

        PageRequest page = PageRequest.of(from / size, size, sorting);

        List<Event> events = eventRepository.findPublicEvents(text, categories, paid, start, end, page);

        // Filter only available events if requested
        if (Boolean.TRUE.equals(onlyAvailable)) {
            Map<Long, Long> confirmedMap = getConfirmedRequestsMap(
                    events.stream().map(Event::getId).collect(Collectors.toList())
            );
            events = events.stream()
                    .filter(e -> e.getParticipantLimit() == 0 ||
                            confirmedMap.getOrDefault(e.getId(), 0L) < e.getParticipantLimit())
                    .collect(Collectors.toList());
        }

        // Fetch views and confirmed requests in bulk
        Map<Long, Long> viewsMap = getViewsForEvents(events);
        Map<Long, Long> confirmedMap = getConfirmedRequestsMap(
                events.stream().map(Event::getId).collect(Collectors.toList())
        );

        List<EventShortDto> dtos = events.stream()
                .map(event -> {
                    EventShortDto dto = eventMapper.toShortDto(event);
                    dto.setConfirmedRequests(confirmedMap.getOrDefault(event.getId(), 0L));
                    dto.setViews(viewsMap.getOrDefault(event.getId(), 0L));
                    return dto;
                })
                .collect(Collectors.toList());

        // Apply VIEWS sorting client-side if requested
        if ("VIEWS".equalsIgnoreCase(sort)) {
            dtos.sort(Comparator.comparing(EventShortDto::getViews).reversed());
            int toIndex = Math.min(from + size, dtos.size());
            if (from > dtos.size()) return List.of();
            dtos = dtos.subList(from, toIndex);
        }

        return dtos;
    }

    @Override
    public EventFullDto getEventById(Long id, String ip) {
        // Отправляем информацию о "хите" в сервис статистики (дубликат из контроллера, но оставляем)
        try {
            statsClient.hit("ewm-main-service", "/events/" + id, ip, LocalDateTime.now());
        } catch (Exception e) {
            log.warn("Failed to send hit to stats service: {}", e.getMessage());
        }

        Event event = eventRepository.findByIdAndState(id, State.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Event with id=" + id + " not found or not published"));

        Long confirmedRequests = requestRepository.countByEventIdAndStatus(id, RequestStatus.CONFIRMED);
        Map<Long, Long> viewsMap = getViewsForEvents(List.of(event));

        EventFullDto dto = eventMapper.toFullDto(event);
        dto.setConfirmedRequests(confirmedRequests);
        dto.setViews(viewsMap.getOrDefault(id, 0L));

        return dto;
    }

    private Map<Long, Long> getConfirmedRequestsMap(List<Long> eventIds) {
        if (eventIds.isEmpty()) return Collections.emptyMap();

        return requestRepository.findAllByEventIdInAndStatus(eventIds, RequestStatus.CONFIRMED).stream()
                .collect(Collectors.groupingBy(
                        req -> req.getEvent().getId(),
                        Collectors.counting()
                ));
    }

    private Map<Long, Long> getViewsForEvents(List<Event> events) {
        if (events.isEmpty()) return Collections.emptyMap();

        List<String> uris = events.stream()
                .map(e -> "/events/" + e.getId())
                .collect(Collectors.toList());

        LocalDateTime start = events.stream()
                .map(Event::getPublishedOn)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(DISTANT_PAST);

        try {
            List<ViewStatsDto> stats = statsClient.getStats(start, LocalDateTime.now(), uris, true);

            Map<Long, Long> viewsMap = new HashMap<>();
            for (ViewStatsDto stat : stats) {
                Matcher matcher = EVENT_URI_PATTERN.matcher(stat.getUri());
                if (matcher.matches()) {
                    Long eventId = Long.parseLong(matcher.group(1));
                    viewsMap.put(eventId, stat.getHits());
                }
            }
            return viewsMap;
        } catch (Exception e) {
            log.warn("Failed to get stats from stats service: {}", e.getMessage());
            return new HashMap<>(); // Возвращаем 0 views в случае ошибки
        }
    }
}