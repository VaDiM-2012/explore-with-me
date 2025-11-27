package ru.practicum.ewm.event.service;

import lombok.RequiredArgsConstructor;
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
public class PrivateEventServiceImpl implements PrivateEventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final EventMapper eventMapper;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional
    public EventFullDto createEvent(Long userId, NewEventDto dto) {
        User initiator = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " not found"));

        Category category = categoryRepository.findById(dto.getCategory())
                .orElseThrow(() -> new NotFoundException("Category with id=" + dto.getCategory() + " not found"));

        LocalDateTime eventDate = LocalDateTime.parse(dto.getEventDate(), FORMATTER);
//        if (eventDate.isBefore(LocalDateTime.now().plusHours(2))) {
//            throw new ValidationException("Event date must be at least 2 hours in the future");
//        }

        Event event = eventMapper.toEvent(dto, initiator, category);
        Event saved = eventRepository.save(event);
        return eventMapper.toFullDto(saved);
    }

    @Override
    public List<EventShortDto> getUserEvents(Long userId, Integer from, Integer size) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User with id=" + userId + " not found");
        }
        return eventRepository.findAllByInitiatorId(userId, of(from / size, size, by("id").ascending())).stream()
                .map(eventMapper::toShortDto)
                .collect(Collectors.toList());
    }

    @Override
    public EventFullDto getUserEventById(Long userId, Long eventId) {
        Event event = eventRepository.findByInitiatorIdAndId(userId, eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " for user=" + userId + " not found"));
        return eventMapper.toFullDto(event);
    }

    @Override
    @Transactional
    public EventFullDto updateEvent(Long userId, Long eventId, UpdateEventUserRequest request) {
        Event event = eventRepository.findByInitiatorIdAndId(userId, eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " for user=" + userId + " not found"));

        if (!event.getState().equals(State.PENDING) && !event.getState().equals(State.CANCELED)) {
            throw new ConflictException("Only PENDING or CANCELED events can be updated");
        }

        Category category = null;
        if (request.getCategory() != null) {
            category = categoryRepository.findById(request.getCategory())
                    .orElseThrow(() -> new NotFoundException("Category with id=" + request.getCategory() + " not found"));
        }

        if (request.getEventDate() != null) {
            LocalDateTime newDate = LocalDateTime.parse(request.getEventDate(), FORMATTER);
//            if (newDate.isBefore(LocalDateTime.now().plusHours(2))) {
//                throw new ValidationException("Event date must be at least 2 hours in the future");
//            }
        }

        eventMapper.updateFromUserRequest(request, event, category);

        if (request.getStateAction() != null) {
            if (request.getStateAction() == StateActionUser.SEND_TO_REVIEW) {
                event.setState(State.PENDING);
            } else if (request.getStateAction() == StateActionUser.CANCEL_REVIEW) {
                event.setState(State.CANCELED);
            }
        }

        Event updated = eventRepository.save(event);
        return eventMapper.toFullDto(updated);
    }
}