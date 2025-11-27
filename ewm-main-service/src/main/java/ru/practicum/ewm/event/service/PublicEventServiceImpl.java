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

        log.info("Начало публичного поиска событий с параметрами: text='{}', categories={}, paid={}, rangeStart={}, rangeEnd={}, onlyAvailable={}, sort={}, from={}, size={}, ip={}",
                text, categories, paid, rangeStart, rangeEnd, onlyAvailable, sort, from, size, ip);

        try {
            statsClient.hit("ewm-main-service", "/events", ip, LocalDateTime.now());
            log.debug("Отправлена статистика о просмотре списка событий с IP: {}", ip);
        } catch (Exception e) {
            log.warn("Не удалось отправить данные в сервис статистики: {}", e.getMessage());
        }

        LocalDateTime start = rangeStart != null ? rangeStart : LocalDateTime.now();
        LocalDateTime end = rangeEnd != null ? rangeEnd : LocalDateTime.now().plusYears(100);

        Sort sorting = "EVENT_DATE".equalsIgnoreCase(sort)
                ? Sort.by("eventDate").ascending()
                : Sort.unsorted();

        PageRequest page = PageRequest.of(from / size, size, sorting);

        List<Event> events = eventRepository.findPublicEvents(text, categories, paid, start, end, page);
        log.debug("Найдено {} событий по фильтрам", events.size());

        // Фильтрация по доступности
        if (Boolean.TRUE.equals(onlyAvailable)) {
            log.debug("Фильтрация событий по доступности участия");
            Map<Long, Long> confirmedMap = getConfirmedRequestsMap(
                    events.stream().map(Event::getId).collect(Collectors.toList())
            );
            int before = events.size();
            events = events.stream()
                    .filter(e -> e.getParticipantLimit() == 0 ||
                            confirmedMap.getOrDefault(e.getId(), 0L) < e.getParticipantLimit())
                    .collect(Collectors.toList());
            log.debug("После фильтрации по доступности осталось {} событий из {}", events.size(), before);
        }

        // Получение статистики просмотров и подтверждённых заявок
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

        // Сортировка по просмотрам на стороне клиента
        if ("VIEWS".equalsIgnoreCase(sort)) {
            log.debug("Сортировка событий по количеству просмотров (по убыванию)");
            dtos.sort(Comparator.comparing(EventShortDto::getViews).reversed());
            int toIndex = Math.min(from + size, dtos.size());
            if (from > dtos.size()) {
                log.debug("Запрошенный диапазон выходит за пределы списка событий: from={} > size={}", from, dtos.size());
                return List.of();
            }
            dtos = dtos.subList(from, toIndex);
        }

        log.info("Возвращено {} событий по публичному запросу", dtos.size());
        return dtos;
    }

    @Override
    public EventFullDto getEventById(Long id, String ip) {
        log.info("Начало получения полной информации о публичном событии: id={}, ip={}", id, ip);

        try {
            statsClient.hit("ewm-main-service", "/events/" + id, ip, LocalDateTime.now());
            log.debug("Отправлена статистика о просмотре события: event_id={}, ip={}", id, ip);
        } catch (Exception e) {
            log.warn("Не удалось отправить данные в сервис статистики: {}", e.getMessage());
        }

        Event event = eventRepository.findByIdAndState(id, State.PUBLISHED)
                .orElseThrow(() -> {
                    log.warn("Событие с ID {} не найдено или не опубликовано", id);
                    return new NotFoundException("Event with id=" + id + " not found or not published");
                });

        log.debug("Событие найдено: title='{}', publishedOn={}", event.getTitle(), event.getPublishedOn());

        Long confirmedRequests = requestRepository.countByEventIdAndStatus(id, RequestStatus.CONFIRMED);
        Map<Long, Long> viewsMap = getViewsForEvents(List.of(event));

        EventFullDto dto = eventMapper.toFullDto(event);
        dto.setConfirmedRequests(confirmedRequests);
        dto.setViews(viewsMap.getOrDefault(id, 0L));

        log.info("Событие возвращено: id={}, title='{}', views={}, confirmedRequests={}",
                dto.getId(), dto.getTitle(), dto.getViews(), dto.getConfirmedRequests());
        return dto;
    }

    private Map<Long, Long> getConfirmedRequestsMap(List<Long> eventIds) {
        if (eventIds.isEmpty()) {
            log.debug("Список eventIds пуст, возвращаем пустую карту подтверждённых заявок");
            return Collections.emptyMap();
        }

        log.debug("Получение количества подтверждённых заявок для {} событий", eventIds.size());
        return requestRepository.findAllByEventIdInAndStatus(eventIds, RequestStatus.CONFIRMED).stream()
                .collect(Collectors.groupingBy(
                        req -> req.getEvent().getId(),
                        Collectors.counting()
                ));
    }

    private Map<Long, Long> getViewsForEvents(List<Event> events) {
        if (events.isEmpty()) {
            log.debug("Список событий пуст, возвращаем пустую карту просмотров");
            return Collections.emptyMap();
        }

        List<String> uris = events.stream()
                .map(e -> "/events/" + e.getId())
                .collect(Collectors.toList());

        LocalDateTime start = events.stream()
                .map(Event::getPublishedOn)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(DISTANT_PAST);

        log.debug("Запрос статистики просмотров для {} событий, начиная с {}", events.size(), start);

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
            log.debug("Получены данные просмотров для {} событий", viewsMap.size());
            return viewsMap;
        } catch (Exception e) {
            log.warn("Не удалось получить статистику просмотров: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}