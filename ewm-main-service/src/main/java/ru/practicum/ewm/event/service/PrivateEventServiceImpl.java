package ru.practicum.ewm.event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.repository.CategoryRepository;
import ru.practicum.ewm.event.dto.EventFullDto;
import ru.practicum.ewm.event.dto.EventShortDto;
import ru.practicum.ewm.event.dto.NewEventDto;
import ru.practicum.ewm.event.dto.UpdateEventUserRequest;
import ru.practicum.ewm.event.mapper.EventMapper;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.State;
import ru.practicum.ewm.event.model.StateActionUser;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.exception.ValidationException;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.data.domain.PageRequest.of;
import static org.springframework.data.domain.Sort.by;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PrivateEventServiceImpl implements PrivateEventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final EventMapper eventMapper;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional
    public EventFullDto createEvent(Long userId, NewEventDto dto) {
        log.info("Начало создания события пользователем с ID: {}", userId);

        User initiator = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Пользователь с ID {} не найден при создании события", userId);
                    return new NotFoundException("User with id=" + userId + " not found");
                });

        Category category = categoryRepository.findById(dto.getCategory())
                .orElseThrow(() -> {
                    log.warn("Категория с ID {} не найдена при создании события", dto.getCategory());
                    return new NotFoundException("Category with id=" + dto.getCategory() + " not found");
                });

        LocalDateTime eventDate = LocalDateTime.parse(dto.getEventDate(), FORMATTER);
        if (eventDate.isBefore(LocalDateTime.now().plusHours(2))) {
            log.warn("Попытка создания события с датой раньше чем через 2 часа: {}", eventDate);
            throw new ValidationException("Event date must be at least 2 hours in the future");
        }

        Event event = eventMapper.toEvent(dto, initiator, category);
        Event saved = eventRepository.save(event);
        log.info("Событие успешно создано с ID: {} и статусом: {}", saved.getId(), saved.getState());
        return eventMapper.toFullDto(saved);
    }

    @Override
    public List<EventShortDto> getUserEvents(Long userId, Integer from, Integer size) {
        log.info("Получен запрос на получение событий пользователя: userId={}, from={}, size={}", userId, from, size);

        if (!userRepository.existsById(userId)) {
            log.warn("Пользователь с ID {} не найден при запросе списка событий", userId);
            throw new NotFoundException("User with id=" + userId + " not found");
        }

        List<EventShortDto> events = eventRepository.findAllByInitiatorId(userId, of(from / size, size, by("id").ascending()))
                .stream()
                .map(eventMapper::toShortDto)
                .collect(Collectors.toList());

        log.info("Пользователь {} имеет {} событий", userId, events.size());
        return events;
    }

    @Override
    public EventFullDto getUserEventById(Long userId, Long eventId) {
        log.info("Получен запрос на получение события: userId={}, eventId={}", userId, eventId);

        Event event = eventRepository.findByInitiatorIdAndId(userId, eventId)
                .orElseThrow(() -> {
                    log.warn("Событие с ID {} не найдено для пользователя {}", eventId, userId);
                    return new NotFoundException("Event with id=" + eventId + " for user=" + userId + " not found");
                });

        log.info("Событие найдено: ID={}, title='{}', state={}", event.getId(), event.getTitle(), event.getState());
        return eventMapper.toFullDto(event);
    }

    @Override
    @Transactional
    public EventFullDto updateEvent(Long userId, Long eventId, UpdateEventUserRequest request) {
        log.info("Начало обновления события: userId={}, eventId={}, action={}", userId, eventId, request.getStateAction());

        Event event = eventRepository.findByInitiatorIdAndId(userId, eventId)
                .orElseThrow(() -> {
                    log.warn("Событие с ID {} не найдено для пользователя {} при обновлении", eventId, userId);
                    return new NotFoundException("Event with id=" + eventId + " for user=" + userId + " not found");
                });

        if (!event.getState().equals(State.PENDING) && !event.getState().equals(State.CANCELED)) {
            log.warn("Невозможно обновить событие {}: текущий статус — {}", eventId, event.getState());
            throw new ConflictException("Only PENDING or CANCELED events can be updated");
        }

        Category category = null;
        if (request.getCategory() != null) {
            category = categoryRepository.findById(request.getCategory())
                    .orElseThrow(() -> {
                        log.warn("Категория с ID {} не найдена при обновлении события", request.getCategory());
                        return new NotFoundException("Category with id=" + request.getCategory() + " not found");
                    });
            log.debug("Категория {} будет обновлена для события {}", request.getCategory(), eventId);
        }

        if (request.getEventDate() != null) {
            LocalDateTime newDate = LocalDateTime.parse(request.getEventDate(), FORMATTER);
            if (newDate.isBefore(LocalDateTime.now().plusHours(2))) {
                log.warn("Попытка установить дату события {} ранее чем через 2 часа: {}", eventId, newDate);
                throw new ValidationException("Event date must be at least 2 hours in the future");
            }
            log.debug("Дата события {} будет обновлена на {}", eventId, newDate);
        }

        eventMapper.updateFromUserRequest(request, event, category);

        if (request.getStateAction() != null) {
            if (request.getStateAction() == StateActionUser.SEND_TO_REVIEW) {
                event.setState(State.PENDING);
                log.info("Событие {} отправлено на модерацию", eventId);
            } else if (request.getStateAction() == StateActionUser.CANCEL_REVIEW) {
                event.setState(State.CANCELED);
                log.info("Событие {} отменено инициатором", eventId);
            }
        }

        Event updated = eventRepository.save(event);
        log.info("Событие {} успешно обновлено, новый статус: {}", updated.getId(), updated.getState());
        return eventMapper.toFullDto(updated);
    }
}