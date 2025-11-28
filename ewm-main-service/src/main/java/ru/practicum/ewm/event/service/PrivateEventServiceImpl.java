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
import java.util.Optional;
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
    private static final int MIN_HOURS_BEFORE_EVENT = 2; // Минимальное время до события

    // --- Методы, которые не менялись, для полноты класса ---

    @Override
    @Transactional
    public EventFullDto createEvent(Long userId, NewEventDto dto) {
        log.info("Начало создания события пользователем с ID: {}", userId);

        User initiator = findUserOrThrow(userId);
        Category category = findCategoryOrThrow(dto.getCategory());

        LocalDateTime eventDate = parseAndValidateEventDate(dto.getEventDate());

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

        Event event = findUserEventOrThrow(userId, eventId);

        log.info("Событие найдено: ID={}, title='{}', state={}", event.getId(), event.getTitle(), event.getState());
        return eventMapper.toFullDto(event);
    }

    // --- Рефакторинг метода updateEvent ---

    /**
     * Обновление события пользователем.
     */
    @Override
    @Transactional
    public EventFullDto updateEvent(Long userId, Long eventId, UpdateEventUserRequest request) {
        log.info("Начало обновления события: userId={}, eventId={}, action={}", userId, eventId, request.getStateAction());

        Event event = findUserEventOrThrow(userId, eventId);

        // 1. Проверка на возможность обновления (PENDING или CANCELED)
        validateUpdatableState(event);

        // 2. Получение категории (если передана)
        Category category = findCategoryIfPresent(request.getCategory());

        // 3. Валидация новой даты события (если передана)
        validateNewEventDateIfPresent(eventId, request.getEventDate());

        // 4. Обновление полей с использованием маппера
        eventMapper.updateFromUserRequest(request, event, category);

        // 5. Обработка действия с состоянием
        processEventStateAction(event, request.getStateAction());

        // 6. Сохранение и возврат результата
        Event updated = eventRepository.save(event);
        log.info("Событие {} успешно обновлено, новый статус: {}", updated.getId(), updated.getState());
        return eventMapper.toFullDto(updated);
    }

    // --- Приватные вспомогательные методы для читаемости ---

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Пользователь с ID {} не найден", userId);
                    return new NotFoundException("User with id=" + userId + " not found");
                });
    }

    private Category findCategoryOrThrow(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> {
                    log.warn("Категория с ID {} не найдена", categoryId);
                    return new NotFoundException("Category with id=" + categoryId + " not found");
                });
    }

    private Category findCategoryIfPresent(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> {
                    log.warn("Категория с ID {} не найдена при обновлении события", categoryId);
                    return new NotFoundException("Category with id=" + categoryId + " not found");
                });
    }

    private Event findUserEventOrThrow(Long userId, Long eventId) {
        return eventRepository.findByInitiatorIdAndId(userId, eventId)
                .orElseThrow(() -> {
                    log.warn("Событие с ID {} не найдено для пользователя {} при запросе/обновлении", eventId, userId);
                    return new NotFoundException("Event with id=" + eventId + " for user=" + userId + " not found");
                });
    }

    private LocalDateTime parseAndValidateEventDate(String eventDateString) {
        LocalDateTime newDate = LocalDateTime.parse(eventDateString, FORMATTER);
        if (newDate.isBefore(LocalDateTime.now().plusHours(MIN_HOURS_BEFORE_EVENT))) {
            log.warn("Попытка установить дату события раньше чем через {} часа(ов): {}", MIN_HOURS_BEFORE_EVENT, newDate);
            throw new ValidationException("Event date must be at least " + MIN_HOURS_BEFORE_EVENT + " hours in the future");
        }
        return newDate;
    }

    private void validateNewEventDateIfPresent(Long eventId, String eventDateString) {
        if (eventDateString != null) {
            LocalDateTime newDate = LocalDateTime.parse(eventDateString, FORMATTER);
            if (newDate.isBefore(LocalDateTime.now().plusHours(MIN_HOURS_BEFORE_EVENT))) {
                log.warn("Попытка установить дату события {} ранее чем через {} часа(ов): {}", eventId, MIN_HOURS_BEFORE_EVENT, newDate);
                throw new ValidationException("Event date must be at least " + MIN_HOURS_BEFORE_EVENT + " hours in the future");
            }
            log.debug("Дата события {} будет обновлена на {}", eventId, newDate);
        }
    }

    private void validateUpdatableState(Event event) {
        if (!event.getState().equals(State.PENDING) && !event.getState().equals(State.CANCELED)) {
            log.warn("Невозможно обновить событие {}: текущий статус — {}", event.getId(), event.getState());
            throw new ConflictException("Only PENDING or CANCELED events can be updated");
        }
    }

    private void processEventStateAction(Event event, StateActionUser action) {
        if (action == null) {
            return;
        }

        if (action == StateActionUser.SEND_TO_REVIEW) {
            event.setState(State.PENDING);
            log.info("Событие {} отправлено на модерацию", event.getId());
        } else if (action == StateActionUser.CANCEL_REVIEW) {
            event.setState(State.CANCELED);
            log.info("Событие {} отменено инициатором", event.getId());
        }
    }
}