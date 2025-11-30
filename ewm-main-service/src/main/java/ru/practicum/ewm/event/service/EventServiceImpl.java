package ru.practicum.ewm.event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.repository.CategoryRepository;
import ru.practicum.ewm.event.dto.*;
import ru.practicum.ewm.event.mapper.EventMapper;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.State;
import ru.practicum.ewm.event.model.StateActionUser;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.exception.ValidationException;
import ru.practicum.ewm.request.model.RequestStatus;
import ru.practicum.ewm.request.repository.RequestRepository;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;
import ru.practicum.stats.client.StatsClient;
import ru.practicum.stats.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.springframework.data.domain.PageRequest.of;
import static org.springframework.data.domain.Sort.by;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final RequestRepository requestRepository;
    private final EventMapper eventMapper;
    private final StatsClient statsClient;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MIN_HOURS_BEFORE_EVENT = 2;
    private static final LocalDateTime DISTANT_PAST = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final Pattern EVENT_URI_PATTERN = Pattern.compile("^/events/(\\d+)$");

    @Override
    public List<EventFullDto> searchEventsForAdmin(List<Long> users,
                                                   List<String> states,
                                                   List<Long> categories,
                                                   LocalDateTime rangeStart,
                                                   LocalDateTime rangeEnd,
                                                   Integer from,
                                                   Integer size) {

        log.info("Админ: поиск событий с фильтрами");

        var pageable = PageRequest.of(from / size, size, Sort.by("id").ascending());

        return eventRepository.findAllByAdminFilters(users, states, categories, rangeStart, rangeEnd, pageable)
                .stream()
                .map(eventMapper::toFullDto)
                .toList();
    }

    @Override
    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest request) {
        log.info("Админ: обновление события {}", eventId);

        Event event = findEventByIdOrThrow(eventId);
        updateEventFieldsFromAdminRequest(event, request);
        processAdminStateAction(event, request.getStateAction());

        Event updated = eventRepository.save(event);
        return eventMapper.toFullDto(updated);
    }

    @Override
    @Transactional
    public EventFullDto createEventByInitiator(Long userId, NewEventDto dto) {
        log.info("Инициатор {} создаёт событие", userId);

        User initiator = findUserByIdOrThrow(userId);
        Category category = findCategoryByIdOrThrow(dto.getCategory());
        LocalDateTime eventDate = parseAndValidateEventDate(dto.getEventDate());

        Event event = eventMapper.toEvent(dto, initiator, category);
        event.setEventDate(eventDate);
        event.setState(State.PENDING);

        Event saved = eventRepository.save(event);
        return eventMapper.toFullDto(saved);
    }

    @Override
    public List<EventShortDto> getEventsByInitiator(Long userId, Integer from, Integer size) {
        log.info("Инициатор {} запрашивает свои события", userId);

        findUserByIdOrThrow(userId); // проверка существования

        var pageable = of(from / size, size, by("id").ascending());
        return eventRepository.findAllByInitiatorId(userId, pageable)
                .stream()
                .map(eventMapper::toShortDto)
                .collect(Collectors.toList());
    }

    public EventFullDto getEventByInitiator(Long userId, Long eventId) {
        Event event = findEventByInitiatorOrThrow(userId, eventId);
        return eventMapper.toFullDto(event);
    }

    @Override
    @Transactional
    public EventFullDto updateEventByInitiator(Long userId, Long eventId, UpdateEventUserRequest request) {
        log.info("Инициатор {} обновляет событие {}", userId, eventId);

        Event event = findEventByInitiatorOrThrow(userId, eventId);
        validateEventIsUpdatableByInitiator(event);

        Category category = findCategoryIfPresent(request.getCategory());
        validateNewEventDateIfPresent(request.getEventDate());

        eventMapper.updateFromUserRequest(request, event, category);
        processInitiatorStateAction(event, request.getStateAction());

        Event updated = eventRepository.save(event);
        return eventMapper.toFullDto(updated);
    }

    @Override
    public List<EventShortDto> getPublishedEvents(String text,
                                                  List<Long> categories,
                                                  Boolean paid,
                                                  LocalDateTime rangeStart,
                                                  LocalDateTime rangeEnd,
                                                  Boolean onlyAvailable,
                                                  String sort,
                                                  Integer from,
                                                  Integer size,
                                                  String ip) {

        log.info("Публичный поиск событий");

        SearchParameters params = initializeSearchParameters(rangeStart, rangeEnd, sort, from, size);
        List<Event> events = eventRepository.findPublicEvents(text, categories, paid, params.start, params.end, params.page);

        events = filterByAvailability(events, onlyAvailable);

        Map<Long, Long> views = getViewsForEvents(events);
        Map<Long, Long> confirmed = getConfirmedRequestsMap(events.stream().map(Event::getId).toList());

        List<EventShortDto> dtos = enrichWithStats(events, views, confirmed);
        return sortAndPaginateByViews(dtos, sort, from, size);
    }

    @Override
    public EventFullDto getPublishedEventById(Long id, String ip) {
        Event event = eventRepository.findByIdAndState(id, State.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Event with id=" + id + " not found or not published"));

        Long confirmed = requestRepository.countByEventIdAndStatus(id, RequestStatus.CONFIRMED);
        Map<Long, Long> views = getViewsForEvents(List.of(event));

        EventFullDto dto = eventMapper.toFullDto(event);
        dto.setConfirmedRequests(confirmed);
        dto.setViews(views.getOrDefault(id, 0L));

        return dto;
    }

    private void updateEventFieldsFromAdminRequest(Event event, UpdateEventAdminRequest request) {
        Optional.ofNullable(request.getAnnotation()).ifPresent(event::setAnnotation);
        Optional.ofNullable(request.getDescription()).ifPresent(event::setDescription);
        Optional.ofNullable(request.getTitle()).ifPresent(event::setTitle);
        Optional.ofNullable(request.getPaid()).ifPresent(event::setPaid);
        Optional.ofNullable(request.getParticipantLimit()).ifPresent(event::setParticipantLimit);
        Optional.ofNullable(request.getRequestModeration()).ifPresent(event::setRequestModeration);
        Optional.ofNullable(request.getLocation()).ifPresent(event::setLocation);

        if (request.getEventDate() != null) {
            if (request.getEventDate().isBefore(LocalDateTime.now().plusHours(MIN_HOURS_BEFORE_EVENT))) {
                throw new ValidationException("Event date must be at least 2 hours in the future");
            }
            event.setEventDate(request.getEventDate());
        }

        if (request.getCategory() != null) {
            Category category = findCategoryByIdOrThrow(request.getCategory());
            event.setCategory(category);
        }
    }

    private void processAdminStateAction(Event event, StateActionAdmin action) {
        if (action == null) return;

        if (action == StateActionAdmin.PUBLISH_EVENT) {
            if (event.getState() != State.PENDING) {
                throw new ConflictException("Cannot publish the event because it's not in PENDING state");
            }
            event.setState(State.PUBLISHED);
            event.setPublishedOn(LocalDateTime.now());
        } else if (action == StateActionAdmin.REJECT_EVENT) {
            if (event.getState() == State.PUBLISHED) {
                throw new ConflictException("Cannot reject the event because it's already published");
            }
            event.setState(State.CANCELED);
        }
    }

    private void processInitiatorStateAction(Event event, StateActionUser action) {
        if (action == null) return;

        if (action == StateActionUser.SEND_TO_REVIEW) {
            event.setState(State.PENDING);
        } else if (action == StateActionUser.CANCEL_REVIEW) {
            event.setState(State.CANCELED);
        }
    }

    private void validateEventIsUpdatableByInitiator(Event event) {
        if (event.getState() != State.PENDING && event.getState() != State.CANCELED) {
            throw new ConflictException("Only PENDING or CANCELED events can be updated by initiator");
        }
    }

    private void validateNewEventDateIfPresent(String eventDateString) {
        if (eventDateString != null) {
            LocalDateTime date = LocalDateTime.parse(eventDateString, FORMATTER);
            if (date.isBefore(LocalDateTime.now().plusHours(MIN_HOURS_BEFORE_EVENT))) {
                throw new ValidationException("Event date must be at least 2 hours in the future");
            }
        }
    }

    private LocalDateTime parseAndValidateEventDate(String dateString) {
        LocalDateTime date = LocalDateTime.parse(dateString, FORMATTER);
        if (date.isBefore(LocalDateTime.now().plusHours(MIN_HOURS_BEFORE_EVENT))) {
            throw new ValidationException("Event date must be at least 2 hours in the future");
        }
        return date;
    }

    private Event findEventByIdOrThrow(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Event with id=" + id + " not found"));
    }

    private Event findEventByInitiatorOrThrow(Long userId, Long eventId) {
        return eventRepository.findByInitiatorIdAndId(userId, eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " not found for user=" + userId));
    }

    private User findUserByIdOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User with id=" + id + " not found"));
    }

    private Category findCategoryByIdOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category with id=" + id + " not found"));
    }

    private Category findCategoryIfPresent(Long id) {
        return id != null ? findCategoryByIdOrThrow(id) : null;
    }

    private record SearchParameters(LocalDateTime start, LocalDateTime end, PageRequest page) {}

    private SearchParameters initializeSearchParameters(LocalDateTime rangeStart, LocalDateTime rangeEnd, String sort, int from, int size) {
        LocalDateTime start = rangeStart != null ? rangeStart : LocalDateTime.now();
        LocalDateTime end = rangeEnd != null ? rangeEnd : LocalDateTime.now().plusYears(100);
        Sort sorting = "EVENT_DATE".equalsIgnoreCase(sort) ? Sort.by("eventDate").ascending() : Sort.unsorted();
        PageRequest page = of(from / size, size, sorting);
        return new SearchParameters(start, end, page);
    }

    private List<Event> filterByAvailability(List<Event> events, Boolean onlyAvailable) {
        if (Boolean.TRUE.equals(onlyAvailable)) {
            Map<Long, Long> confirmed = getConfirmedRequestsMap(events.stream().map(Event::getId).toList());
            return events.stream()
                    .filter(e -> e.getParticipantLimit() == 0 ||
                            confirmed.getOrDefault(e.getId(), 0L) < e.getParticipantLimit())
                    .toList();
        }
        return events;
    }

    private List<EventShortDto> enrichWithStats(List<Event> events, Map<Long, Long> views, Map<Long, Long> confirmed) {
        return events.stream()
                .map(e -> {
                    EventShortDto dto = eventMapper.toShortDto(e);
                    dto.setViews(views.getOrDefault(e.getId(), 0L));
                    dto.setConfirmedRequests(confirmed.getOrDefault(e.getId(), 0L));
                    return dto;
                })
                .toList();
    }

    private List<EventShortDto> sortAndPaginateByViews(List<EventShortDto> dtos, String sort, int from, int size) {
        if ("VIEWS".equalsIgnoreCase(sort)) {
            dtos = dtos.stream()
                    .sorted(Comparator.comparing(EventShortDto::getViews).reversed())
                    .toList();
            int to = Math.min(from + size, dtos.size());
            return from >= dtos.size() ? List.of() : dtos.subList(from, to);
        }
        return dtos;
    }

    private Map<Long, Long> getConfirmedRequestsMap(List<Long> eventIds) {
        if (eventIds.isEmpty()) return Map.of();
        return requestRepository.findAllByEventIdInAndStatus(eventIds, RequestStatus.CONFIRMED)
                .stream()
                .collect(Collectors.groupingBy(r -> r.getEvent().getId(), Collectors.counting()));
    }

    private Map<Long, Long> getViewsForEvents(List<Event> events) {
        if (events.isEmpty()) return Map.of();

        List<String> uris = events.stream().map(e -> "/events/" + e.getId()).toList();
        LocalDateTime start = events.stream()
                .map(Event::getPublishedOn)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(DISTANT_PAST);

        try {
            List<ViewStatsDto> stats = statsClient.getStats(start, LocalDateTime.now(), uris, true);
            Map<Long, Long> views = new HashMap<>();
            for (ViewStatsDto s : stats) {
                Matcher m = EVENT_URI_PATTERN.matcher(s.getUri());
                if (m.matches()) {
                    views.put(Long.parseLong(m.group(1)), s.getHits());
                }
            }
            return views;
        } catch (Exception e) {
            log.warn("Failed to get stats: {}", e.getMessage());
            return Map.of();
        }
    }
}