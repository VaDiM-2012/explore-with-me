package ru.practicum.ewm.request.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.State;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.request.dto.EventRequestStatusUpdateRequest;
import ru.practicum.ewm.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.ewm.request.dto.ParticipationRequestDto;
import ru.practicum.ewm.request.mapper.RequestMapper;
import ru.practicum.ewm.request.model.ParticipationRequest;
import ru.practicum.ewm.request.model.RequestStatus;
import ru.practicum.ewm.request.repository.RequestRepository;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class RequestServiceImpl implements RequestService {

    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final RequestMapper requestMapper;

    /**
     * Создание запроса на участие в событии.
     */
    @Override
    @Transactional
    public ParticipationRequestDto createRequest(Long userId, Long eventId) {
        log.info("Создание запроса на участие пользователя={} в событии={}", userId, eventId);

        // 1. Поиск сущностей
        User user = findUserOrThrow(userId);
        Event event = findEventOrThrow(eventId);

        // 2. Валидация возможности создания запроса
        validateRequestCreation(userId, eventId, event);

        // 3. Проверка лимита
        long confirmed = checkLimitAndGetConfirmed(eventId, event.getParticipantLimit());

        // 4. Определение статуса
        RequestStatus status = determineInitialStatus(event, confirmed);

        // 5. Создание и сохранение запроса
        ParticipationRequest request = ParticipationRequest.builder()
                .requester(user)
                .event(event)
                .status(status)
                .build();

        ParticipationRequest saved = requestRepository.save(request);
        log.info("Запрос на участие создан с id={}", saved.getId());
        return requestMapper.toDto(saved);
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Пользователь с id={} не найден", userId);
                    return new NotFoundException("User with id=" + userId + " not found");
                });
    }

    private Event findEventOrThrow(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    log.warn("Событие с id={} не найдено", eventId);
                    return new NotFoundException("Event with id=" + eventId + " not found");
                });
    }

    private void validateRequestCreation(Long userId, Long eventId, Event event) {
        if (event.getInitiator().getId().equals(userId)) {
            log.warn("Пользователь={} является инициатором события={}, не может подать заявку", userId, eventId);
            throw new ConflictException("Initiator cannot request participation in own event");
        }

        if (!event.getState().equals(State.PUBLISHED)) {
            log.warn("Событие={} не опубликовано, участие невозможно", eventId);
            throw new ConflictException("Cannot participate in unpublished event");
        }

        if (requestRepository.existsByRequesterIdAndEventId(userId, eventId)) {
            log.warn("Повторный запрос на участие пользователя={} в событии={}", userId, eventId);
            throw new ConflictException("Duplicate participation request");
        }
    }

    private long checkLimitAndGetConfirmed(Long eventId, int limit) {
        long confirmed = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        if (limit > 0 && confirmed >= limit) {
            log.warn("Достигнут лимит участников для события={}", eventId);
            throw new ConflictException("Participant limit reached");
        }
        return confirmed;
    }

    private RequestStatus determineInitialStatus(Event event, long confirmed) {
        RequestStatus status = (event.getParticipantLimit() == 0 || !event.getRequestModeration())
                ? RequestStatus.CONFIRMED : RequestStatus.PENDING;

        log.info("Статус запроса установлен как {} для события={}", status, event.getId());
        return status;
    }

    @Override
    public List<ParticipationRequestDto> getUserRequests(Long userId) {
        log.info("Получение запросов на участие для пользователя={}", userId);

        if (!userRepository.existsById(userId)) {
            log.warn("Пользователь с id={} не найден", userId);
            throw new NotFoundException("User with id=" + userId + " not found");
        }

        List<ParticipationRequestDto> requests = requestRepository.findAllByRequesterId(userId).stream()
                .map(requestMapper::toDto)
                .collect(Collectors.toList());

        log.info("Найдено {} запросов на участие для пользователя={}", requests.size(), userId);
        return requests;
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        log.info("Отмена запроса id={} для пользователя={}", requestId, userId);

        ParticipationRequest request = requestRepository.findByIdAndRequesterId(requestId, userId)
                .orElseThrow(() -> {
                    log.warn("Запрос с id={} для пользователя={} не найден", requestId, userId);
                    return new NotFoundException("Request with id=" + requestId + " for user=" + userId + " not found");
                });

        if (!request.getStatus().equals(RequestStatus.PENDING)) {
            log.warn("Запрос id={} не в статусе ожидания, отмена невозможна", requestId);
            throw new ConflictException("Only pending requests can be canceled");
        }

        request.setStatus(RequestStatus.CANCELED);
        ParticipationRequest updated = requestRepository.save(request);
        log.info("Запрос id={} успешно отменён", requestId);
        return requestMapper.toDto(updated);
    }

    @Override
    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        log.info("Получение запросов на участие в событии={} от инициатора={}", eventId, userId);

        eventRepository.findByInitiatorIdAndId(userId, eventId)
                .orElseThrow(() -> {
                    log.warn("Событие с id={} для пользователя={} не найдено", eventId, userId);
                    return new NotFoundException("Event with id=" + eventId + " for user=" + userId + " not found");
                });

        List<ParticipationRequestDto> requests = requestRepository.findAllByEventId(eventId).stream()
                .map(requestMapper::toDto)
                .collect(Collectors.toList());

        log.info("Найдено {} запросов для события={}", requests.size(), eventId);
        return requests;
    }

    /**
     * Обновление статусов заявок инициатором события.
     */
    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateRequestsStatus(Long userId,
                                                               Long eventId,
                                                               EventRequestStatusUpdateRequest updateRequest) {
        log.info("Обновление статусов заявок для события={} инициатором={}. Id заявок: {}, новый статус: {}",
                eventId, userId, updateRequest.getRequestIds(), updateRequest.getStatus());

        // 1. Предварительные проверки и получение событий/запросов
        Event event = findInitiatorEventOrThrow(userId, eventId);
        validateModerationRequirements(eventId, event);

        List<ParticipationRequest> requestsToUpdate = getValidPendingRequests(eventId, updateRequest.getRequestIds());

        RequestStatus newStatus = RequestStatus.valueOf(updateRequest.getStatus());
        List<ParticipationRequestDto> confirmed = new ArrayList<>();
        List<ParticipationRequestDto> rejected = new ArrayList<>();

        if (newStatus == RequestStatus.CONFIRMED) {
            processConfirmation(event, requestsToUpdate, confirmed, rejected);
        } else if (newStatus == RequestStatus.REJECTED) {
            processRejection(requestsToUpdate, rejected);
        }

        log.info("Обновление статусов заявок завершено: подтверждено {}, отклонено {}", confirmed.size(), rejected.size());
        return new EventRequestStatusUpdateResult(confirmed, rejected);
    }

    private Event findInitiatorEventOrThrow(Long userId, Long eventId) {
        return eventRepository.findByInitiatorIdAndId(userId, eventId)
                .orElseThrow(() -> {
                    log.warn("Событие с id={} для пользователя={} не найдено", eventId, userId);
                    return new NotFoundException("Event with id=" + eventId + " for user=" + userId + " not found");
                });
    }

    private void validateModerationRequirements(Long eventId, Event event) {
        if (!event.getRequestModeration() || event.getParticipantLimit() == 0) {
            log.warn("Для события={} не требуется модерация заявок", eventId);
            throw new ConflictException("No moderation required for this event");
        }
    }

    private List<ParticipationRequest> getValidPendingRequests(Long eventId, List<Long> requestIds) {
        List<ParticipationRequest> requests = requestRepository.findAllByEventIdAndIdIn(eventId, requestIds);

        if (requests.size() != requestIds.size()) {
            log.warn("Некоторые заявки не найдены для события={}", eventId);
        }

        if (requests.stream().anyMatch(r -> !r.getStatus().equals(RequestStatus.PENDING))) {
            log.warn("Попытка обновить заявки не в статусе ожидания: {}", requestIds);
            throw new ConflictException("Only pending requests can be moderated");
        }
        return requests;
    }

    /**
     * Обрабатывает подтверждение заявок, учитывая лимит.
     */
    private void processConfirmation(Event event,
                                     List<ParticipationRequest> requestsToConfirm,
                                     List<ParticipationRequestDto> confirmed,
                                     List<ParticipationRequestDto> rejected) {

        long currentConfirmed = requestRepository.countByEventIdAndStatus(event.getId(), RequestStatus.CONFIRMED);
        long available = event.getParticipantLimit() - currentConfirmed;

        if (available <= 0) {
            log.warn("Достигнут лимит участников для события={}", event.getId());
            throw new ConflictException("Participant limit reached");
        }

        int toConfirm = (int) Math.min(available, requestsToConfirm.size());

        // 1. Подтверждение доступных заявок
        for (int i = 0; i < toConfirm; i++) {
            ParticipationRequest req = requestsToConfirm.get(i);
            req.setStatus(RequestStatus.CONFIRMED);
            requestRepository.save(req);
            confirmed.add(requestMapper.toDto(req));
            log.info("Заявка id={} подтверждена", req.getId());
        }

        // 2. Отклонение заявок, превышающих лимит (из текущего списка)
        for (int i = toConfirm; i < requestsToConfirm.size(); i++) {
            ParticipationRequest req = requestsToConfirm.get(i);
            req.setStatus(RequestStatus.REJECTED);
            requestRepository.save(req);
            rejected.add(requestMapper.toDto(req));
            log.info("Заявка id={} отклонена (превышение лимита)", req.getId());
        }

        // 3. Автоматическое отклонение всех остальных ожидающих заявок, если лимит достигнут
        if (event.getParticipantLimit() > 0 && available == toConfirm) {
            autoRejectOtherPendingRequests(event.getId(), requestsToConfirm, rejected);
        }
    }

    /**
     * Обрабатывает простое отклонение заявок.
     */
    private void processRejection(List<ParticipationRequest> requestsToReject, List<ParticipationRequestDto> rejected) {
        for (ParticipationRequest req : requestsToReject) {
            req.setStatus(RequestStatus.REJECTED);
            requestRepository.save(req);
            rejected.add(requestMapper.toDto(req));
            log.info("Заявка id={} отклонена", req.getId());
        }
    }

    /**
     * Автоматически отклоняет остальные ожидающие запросы после достижения лимита.
     */
    private void autoRejectOtherPendingRequests(Long eventId,
                                                List<ParticipationRequest> currentProcessedRequests,
                                                List<ParticipationRequestDto> rejected) {

        List<Long> currentlyProcessedIds = currentProcessedRequests.stream()
                .map(ParticipationRequest::getId)
                .toList();

        List<ParticipationRequest> otherPending = requestRepository.findAllByEventId(eventId).stream()
                .filter(r -> r.getStatus() == RequestStatus.PENDING && !currentlyProcessedIds.contains(r.getId()))
                .toList();

        for (ParticipationRequest req : otherPending) {
            req.setStatus(RequestStatus.REJECTED);
            requestRepository.save(req);
            rejected.add(requestMapper.toDto(req));
            log.info("Заявка id={} автоматически отклонена из-за достижения лимита", req.getId());
        }
    }
}