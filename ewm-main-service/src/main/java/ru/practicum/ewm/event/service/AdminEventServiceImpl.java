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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminEventServiceImpl implements AdminEventService {

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

        // Исправление: конвертируем строки в enum State (игнорируем неизвестные)
        List<State> stateEnums = null;
        if (states != null && !states.isEmpty()) {
            stateEnums = states.stream()
                    .map(s -> {
                        try {
                            return State.valueOf(s.toUpperCase());
                        } catch (IllegalArgumentException e) {
                            return null; // или можно бросить ValidationException
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();

            if (stateEnums.isEmpty()) {
                stateEnums = null; // если все значения некорректны — ищем без фильтра по state
            }
        }

        // Правильная пагинация (аналогично тому, что было в CategoryServiceImpl)
        int page = from == 0 ? 0 : from / size;
        var pageable = PageRequest.of(page, size, Sort.by("id").ascending());

        return eventRepository.findAllByAdminFilters(
                        users,
                        stateEnums,          // передаём уже enum, а не строки
                        categories,
                        rangeStart,
                        rangeEnd,
                        pageable)
                .stream()
                .map(eventMapper::toFullDto)
                .toList();
    }

    @Override
    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " not found"));

        // Обновление полей (если переданы)
        if (request.getAnnotation() != null) event.setAnnotation(request.getAnnotation());
        if (request.getDescription() != null) event.setDescription(request.getDescription());
        if (request.getTitle() != null) event.setTitle(request.getTitle());
        if (request.getPaid() != null) event.setPaid(request.getPaid());
        if (request.getParticipantLimit() != null) event.setParticipantLimit(request.getParticipantLimit());
        if (request.getRequestModeration() != null) event.setRequestModeration(request.getRequestModeration());
        if (request.getLocation() != null) event.setLocation(request.getLocation());

        if (request.getEventDate() != null) {
            if (request.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
                throw new ru.practicum.ewm.exception.ValidationException("Event date must be at least 2 hours in the future");
            }
            event.setEventDate(request.getEventDate());
        }

        if (request.getCategory() != null) {
            Category category = categoryRepository.findById(request.getCategory())
                    .orElseThrow(() -> new NotFoundException("Category with id=" + request.getCategory() + " not found"));
            event.setCategory(category);
        }

        // Обработка действия с состоянием
        if (request.getStateAction() != null) {
            if (request.getStateAction() == StateActionAdmin.PUBLISH_EVENT) {
                if (event.getState() != State.PENDING) {
                    throw new ConflictException("Cannot publish the event because it's not in PENDING state");
                }
                event.setState(State.PUBLISHED);
                event.setPublishedOn(LocalDateTime.now());

            } else if (request.getStateAction() == StateActionAdmin.REJECT_EVENT) {
                if (event.getState() == State.PUBLISHED) {
                    throw new ConflictException("Cannot reject the event because it's already published");
                }
                event.setState(State.CANCELED);
            }
        }

        Event updated = eventRepository.save(event);
        return eventMapper.toFullDto(updated);
    }
}