package ru.practicum.ewm.event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.repository.CategoryRepository;
import ru.practicum.ewm.event.dto.EventFullDto;
import ru.practicum.ewm.event.dto.StateActionAdmin;
import ru.practicum.ewm.event.dto.UpdateEventAdminRequest;
import ru.practicum.ewm.event.mapper.EventMapper;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.State;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.exception.ValidationException; // Добавлен импорт для корректного класса ValidationException
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AdminEventServiceImpl implements AdminEventService {

    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;
    private final EventMapper eventMapper;

    // Минимальное время до события для админского обновления
    private static final int MIN_HOURS_BEFORE_EVENT = 2;

    /**
     * Поиск событий с возможностью фильтрации для администратора.
     */
    @Override
    public List<EventFullDto> searchEvents(List<Long> users,
                                           List<String> states,
                                           List<Long> categories,
                                           LocalDateTime rangeStart,
                                           LocalDateTime rangeEnd,
                                           Integer from,
                                           Integer size) {

        log.info("Администратор запрашивает события с фильтрами: users={}, states={}, categories={}, rangeStart={}, rangeEnd={}, from={}, size={}",
                users, states, categories, rangeStart, rangeEnd, from, size);

        // Создание Pageable с использованием PageRequest.of(page, size, sort)
        // page = from / size
        var pageable = PageRequest.of(from / size, size, Sort.by("id").ascending());

        List<EventFullDto> events = eventRepository.findAllByAdminFilters(users, states, categories, rangeStart, rangeEnd, pageable)
                .stream()
                .map(eventMapper::toFullDto)
                .toList();

        log.info("Найдено {} событий по заданным фильтрам", events.size());
        return events;
    }

    /**
     * Обновление события администратором.
     */
    @Override
    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest request) {
        log.info("Начало обновления события администратором: eventId={}, action={}", eventId, request.getStateAction());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    log.warn("Событие с ID {} не найдено при попытке обновления администратором", eventId);
                    return new NotFoundException("Event with id=" + eventId + " not found");
                });

        // 1. Обновление полей события, если они переданы в запросе
        updateEventFields(event, request);

        // 2. Обработка действия с состоянием события (PUBLISH/REJECT)
        processEventStateAction(event, request.getStateAction());

        // 3. Сохранение и возврат результата
        Event updated = eventRepository.save(event);
        log.info("Событие {} успешно обновлено администратором", updated.getId());
        return eventMapper.toFullDto(updated);
    }

    /**
     * Приватный метод для обновления полей события на основе запроса.
     * @param event Событие, которое нужно обновить.
     * @param request Запрос на обновление.
     */
    private void updateEventFields(Event event, UpdateEventAdminRequest request) {
        Optional.ofNullable(request.getAnnotation())
                .ifPresent(annotation -> {
                    event.setAnnotation(annotation);
                    log.debug("Обновлено поле 'annotation' для события {}", event.getId());
                });

        Optional.ofNullable(request.getDescription())
                .ifPresent(description -> {
                    event.setDescription(description);
                    log.debug("Обновлено поле 'description' для события {}", event.getId());
                });

        Optional.ofNullable(request.getTitle())
                .ifPresent(title -> {
                    event.setTitle(title);
                    log.debug("Обновлено поле 'title' для события {}", event.getId());
                });

        Optional.ofNullable(request.getPaid())
                .ifPresent(paid -> {
                    event.setPaid(paid);
                    log.debug("Обновлено поле 'paid' для события {}", event.getId());
                });

        Optional.ofNullable(request.getParticipantLimit())
                .ifPresent(limit -> {
                    event.setParticipantLimit(limit);
                    log.debug("Обновлено поле 'participantLimit' для события {}", event.getId());
                });

        Optional.ofNullable(request.getRequestModeration())
                .ifPresent(moderation -> {
                    event.setRequestModeration(moderation);
                    log.debug("Обновлено поле 'requestModeration' для события {}", event.getId());
                });

        Optional.ofNullable(request.getLocation())
                .ifPresent(location -> {
                    event.setLocation(location);
                    log.debug("Обновлено поле 'location' для события {}", event.getId());
                });

        updateEventDate(event, request.getEventDate());
        updateEventCategory(event, request.getCategory());
    }

    /**
     * Приватный метод для обновления даты события с проверкой.
     */
    private void updateEventDate(Event event, LocalDateTime newEventDate) {
        if (newEventDate != null) {
            if (newEventDate.isBefore(LocalDateTime.now().plusHours(MIN_HOURS_BEFORE_EVENT))) {
                log.warn("Попытка установить дату события {} ранее чем через {} часа(ов) от текущего времени", event.getId(), MIN_HOURS_BEFORE_EVENT);
                throw new ValidationException("Event date must be at least " + MIN_HOURS_BEFORE_EVENT + " hours in the future");
            }
            event.setEventDate(newEventDate);
            log.debug("Обновлено поле 'eventDate' для события {}", event.getId());
        }
    }

    /**
     * Приватный метод для обновления категории события.
     */
    private void updateEventCategory(Event event, Long newCategoryId) {
        if (newCategoryId != null) {
            Category category = categoryRepository.findById(newCategoryId)
                    .orElseThrow(() -> {
                        log.warn("Категория с ID {} не найдена при обновлении события {}", newCategoryId, event.getId());
                        return new NotFoundException("Category with id=" + newCategoryId + " not found");
                    });
            event.setCategory(category);
            log.debug("Категория обновлена на {} для события {}", newCategoryId, event.getId());
        }
    }

    /**
     * Приватный метод для обработки действий по изменению состояния события.
     * @param event Событие.
     * @param action Действие (PUBLISH_EVENT или REJECT_EVENT).
     */
    private void processEventStateAction(Event event, StateActionAdmin action) {
        if (action == null) {
            return;
        }

        switch (action) {
            case PUBLISH_EVENT:
                handlePublishEvent(event);
                break;
            case REJECT_EVENT:
                handleRejectEvent(event);
                break;
        }
    }

    /**
     * Приватный метод для публикации события.
     */
    private void handlePublishEvent(Event event) {
        if (event.getState() != State.PENDING) {
            log.warn("Невозможно опубликовать событие {}: текущий статус — {}", event.getId(), event.getState());
            throw new ConflictException("Cannot publish the event because it's not in PENDING state");
        }
        event.setState(State.PUBLISHED);
        event.setPublishedOn(LocalDateTime.now());
        log.info("Событие {} успешно опубликовано", event.getId());
    }

    /**
     * Приватный метод для отклонения события.
     */
    private void handleRejectEvent(Event event) {
        if (event.getState() == State.PUBLISHED) {
            log.warn("Невозможно отклонить опубликованное событие {}", event.getId());
            throw new ConflictException("Cannot reject the event because it's already published");
        }
        event.setState(State.CANCELED);
        log.info("Событие {} отклонено и переведено в статус CANCELED", event.getId());
    }
}