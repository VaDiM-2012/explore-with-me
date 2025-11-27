package ru.practicum.ewm.event.service;

import lombok.RequiredArgsConstructor;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminEventServiceImpl implements AdminEventService {

    private static final Logger log = LoggerFactory.getLogger(AdminEventServiceImpl.class);

    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;
    private final EventMapper eventMapper;

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

        var pageable = PageRequest.of(from / size, size, Sort.by("id").ascending());

        List<EventFullDto> events = eventRepository.findAllByAdminFilters(users, states, categories, rangeStart, rangeEnd, pageable)
                .stream()
                .map(eventMapper::toFullDto)
                .toList();

        log.info("Найдено {} событий по заданным фильтрам", events.size());
        return events;
    }

    @Override
    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest request) {
        log.info("Начало обновления события администратором: eventId={}, action={}", eventId, request.getStateAction());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    log.warn("Событие с ID {} не найдено при попытке обновления администратором", eventId);
                    return new NotFoundException("Event with id=" + eventId + " not found");
                });

        // Обновление полей (если переданы)
        if (request.getAnnotation() != null) {
            event.setAnnotation(request.getAnnotation());
            log.debug("Обновлено поле 'annotation' для события {}", eventId);
        }
        if (request.getDescription() != null) {
            event.setDescription(request.getDescription());
            log.debug("Обновлено поле 'description' для события {}", eventId);
        }
        if (request.getTitle() != null) {
            event.setTitle(request.getTitle());
            log.debug("Обновлено поле 'title' для события {}", eventId);
        }
        if (request.getPaid() != null) {
            event.setPaid(request.getPaid());
            log.debug("Обновлено поле 'paid' для события {}", eventId);
        }
        if (request.getParticipantLimit() != null) {
            event.setParticipantLimit(request.getParticipantLimit());
            log.debug("Обновлено поле 'participantLimit' для события {}", eventId);
        }
        if (request.getRequestModeration() != null) {
            event.setRequestModeration(request.getRequestModeration());
            log.debug("Обновлено поле 'requestModeration' для события {}", eventId);
        }
        if (request.getLocation() != null) {
            event.setLocation(request.getLocation());
            log.debug("Обновлено поле 'location' для события {}", eventId);
        }

        if (request.getEventDate() != null) {
            if (request.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
                log.warn("Попытка установить дату события {} ранее чем через 2 часа от текущего времени", eventId);
                throw new ru.practicum.ewm.exception.ValidationException("Event date must be at least 2 hours in the future");
            }
            event.setEventDate(request.getEventDate());
            log.debug("Обновлено поле 'eventDate' для события {}", eventId);
        }

        if (request.getCategory() != null) {
            Category category = categoryRepository.findById(request.getCategory())
                    .orElseThrow(() -> {
                        log.warn("Категория с ID {} не найдена при обновлении события {}", request.getCategory(), eventId);
                        return new NotFoundException("Category with id=" + request.getCategory() + " not found");
                    });
            event.setCategory(category);
            log.debug("Категория обновлена на {} для события {}", request.getCategory(), eventId);
        }

        // Обработка действия с состоянием
        if (request.getStateAction() != null) {
            if (request.getStateAction() == StateActionAdmin.PUBLISH_EVENT) {
                if (event.getState() != State.PENDING) {
                    log.warn("Невозможно опубликовать событие {}: текущий статус — {}", eventId, event.getState());
                    throw new ConflictException("Cannot publish the event because it's not in PENDING state");
                }
                event.setState(State.PUBLISHED);
                event.setPublishedOn(LocalDateTime.now());
                log.info("Событие {} успешно опубликовано", eventId);

            } else if (request.getStateAction() == StateActionAdmin.REJECT_EVENT) {
                if (event.getState() == State.PUBLISHED) {
                    log.warn("Невозможно отклонить опубликованное событие {}", eventId);
                    throw new ConflictException("Cannot reject the event because it's already published");
                }
                event.setState(State.CANCELED);
                log.info("Событие {} отклонено и переведено в статус CANCELED", eventId);
            }
        }

        Event updated = eventRepository.save(event);
        log.info("Событие {} успешно обновлено администратором", updated.getId());
        return eventMapper.toFullDto(updated);
    }
}