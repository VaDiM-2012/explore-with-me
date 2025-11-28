package ru.practicum.ewm.event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class PublicEventServiceImpl implements PublicEventService {

    private final EventRepository eventRepository;
    private final RequestRepository requestRepository;
    private final EventMapper eventMapper;
    private final StatsClient statsClient;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final LocalDateTime DISTANT_PAST = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
    private static final Pattern EVENT_URI_PATTERN = Pattern.compile("^/events/(\\d+)$");

    /**
     * Публичный поиск событий с возможностью фильтрации и сортировки.
     */
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

        // 1. Инициализация параметров поиска и пагинации
        SearchParameters params = initializeSearchParameters(rangeStart, rangeEnd, sort, from, size);

        // 2. Поиск событий в базе данных
        List<Event> events = eventRepository.findPublicEvents(text, categories, paid, params.start, params.end, params.page);
        log.debug("Найдено {} событий по фильтрам до фильтрации по доступности", events.size());

        // 3. Фильтрация по доступности (если требуется)
        events = filterByAvailability(events, onlyAvailable);

        // 4. Получение статистики (просмотры и подтвержденные заявки)
        Map<Long, Long> viewsMap = getViewsForEvents(events);
        Map<Long, Long> confirmedMap = getConfirmedRequestsMap(
                events.stream().map(Event::getId).collect(Collectors.toList())
        );

        // 5. Преобразование в DTO и обогащение статистикой
        List<EventShortDto> dtos = enrichDtosWithStats(events, viewsMap, confirmedMap);

        // 6. Сортировка по просмотрам и клиентская пагинация (если требуется)
        dtos = sortAndPaginateByViews(dtos, sort, from, size);

        log.info("Возвращено {} событий по публичному запросу", dtos.size());
        return dtos;
    }

    // --- Приватные вспомогательные методы ---

    /**
     * Структура для инициализации параметров поиска.
     */
    private static class SearchParameters {
        final LocalDateTime start;
        final LocalDateTime end;
        final Sort sorting;
        final PageRequest page;

        SearchParameters(LocalDateTime start, LocalDateTime end, Sort sorting, PageRequest page) {
            this.start = start;
            this.end = end;
            this.sorting = sorting;
            this.page = page;
        }
    }

    /**
     * Инициализирует параметры поиска (даты, сортировка, пагинация).
     */
    private SearchParameters initializeSearchParameters(LocalDateTime rangeStart,
                                                        LocalDateTime rangeEnd,
                                                        String sort,
                                                        Integer from,
                                                        Integer size) {

        LocalDateTime start = Optional.ofNullable(rangeStart).orElse(LocalDateTime.now());
        LocalDateTime end = Optional.ofNullable(rangeEnd).orElse(LocalDateTime.now().plusYears(100));

        Sort sorting = "EVENT_DATE".equalsIgnoreCase(sort)
                ? Sort.by("eventDate").ascending()
                : Sort.unsorted();

        PageRequest page = PageRequest.of(from / size, size, sorting);

        return new SearchParameters(start, end, sorting, page);
    }

    /**
     * Фильтрует список событий, оставляя только те, где есть свободные места.
     */
    private List<Event> filterByAvailability(List<Event> events, Boolean onlyAvailable) {
        if (Boolean.TRUE.equals(onlyAvailable)) {
            log.debug("Фильтрация событий по доступности участия");

            // Если фильтруем, нужно получить подтвержденные заявки
            Map<Long, Long> confirmedMap = getConfirmedRequestsMap(
                    events.stream().map(Event::getId).collect(Collectors.toList())
            );

            int before = events.size();
            events = events.stream()
                    .filter(e -> e.getParticipantLimit() == 0 || // Лимит 0 = без ограничений
                            confirmedMap.getOrDefault(e.getId(), 0L) < e.getParticipantLimit())
                    .collect(Collectors.toList());
            log.debug("После фильтрации по доступности осталось {} событий из {}", events.size(), before);
        }
        return events;
    }

    /**
     * Обогащает DTO событий данными о просмотрах и подтвержденных заявках.
     */
    private List<EventShortDto> enrichDtosWithStats(List<Event> events,
                                                    Map<Long, Long> viewsMap,
                                                    Map<Long, Long> confirmedMap) {
        return events.stream()
                .map(event -> {
                    EventShortDto dto = eventMapper.toShortDto(event);
                    dto.setConfirmedRequests(confirmedMap.getOrDefault(event.getId(), 0L));
                    dto.setViews(viewsMap.getOrDefault(event.getId(), 0L));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Сортирует DTO событий по просмотрам (если требуется) и выполняет клиентскую пагинацию.
     */
    private List<EventShortDto> sortAndPaginateByViews(List<EventShortDto> dtos,
                                                       String sort,
                                                       Integer from,
                                                       Integer size) {
        if ("VIEWS".equalsIgnoreCase(sort)) {
            log.debug("Сортировка событий по количеству просмотров (по убыванию)");
            dtos.sort(Comparator.comparing(EventShortDto::getViews).reversed());

            // Клиентская пагинация после сортировки по просмотрам:
            int toIndex = Math.min(from + size, dtos.size());
            if (from >= dtos.size()) {
                log.debug("Запрошенный диапазон выходит за пределы списка событий: from={} >= size={}", from, dtos.size());
                return List.of();
            }
            dtos = dtos.subList(from, toIndex);
        }
        return dtos;
    }

    @Override
    public EventFullDto getEventById(Long id, String ip) {
        log.info("Начало получения полной информации о публичном событии: id={}, ip={}", id, ip);

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