package ru.practicum.ewm.request.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class RequestServiceImpl implements RequestService {

    private static final Logger log = LoggerFactory.getLogger(RequestServiceImpl.class);

    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final RequestMapper requestMapper;

    @Override
    @Transactional
    public ParticipationRequestDto createRequest(Long userId, Long eventId) {
        log.info("Creating participation request for user={} on event={}", userId, eventId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("User with id={} not found", userId);
                    return new NotFoundException("User with id=" + userId + " not found");
                });

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    log.warn("Event with id={} not found", eventId);
                    return new NotFoundException("Event with id=" + eventId + " not found");
                });

        if (event.getInitiator().getId().equals(userId)) {
            log.warn("User={} is initiator of event={}, cannot request participation", userId, eventId);
            throw new ConflictException("Initiator cannot request participation in own event");
        }

        if (!event.getState().equals(State.PUBLISHED)) {
            log.warn("Event={} is not published, cannot participate", eventId);
            throw new ConflictException("Cannot participate in unpublished event");
        }

        if (requestRepository.existsByRequesterIdAndEventId(userId, eventId)) {
            log.warn("Duplicate participation request for user={} on event={}", userId, eventId);
            throw new ConflictException("Duplicate participation request");
        }

        long confirmed = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        if (event.getParticipantLimit() > 0 && confirmed >= event.getParticipantLimit()) {
            log.warn("Participant limit reached for event={}", eventId);
            throw new ConflictException("Participant limit reached");
        }

        RequestStatus status = (event.getParticipantLimit() == 0 || !event.getRequestModeration())
                ? RequestStatus.CONFIRMED : RequestStatus.PENDING;

        log.info("Request status set to {} for user={} on event={}", status, userId, eventId);

        ParticipationRequest request = ParticipationRequest.builder()
                .requester(user)
                .event(event)
                .status(status)
                .build();

        ParticipationRequest saved = requestRepository.save(request);
        log.info("Participation request created with id={}", saved.getId());
        return requestMapper.toDto(saved);
    }

    @Override
    public List<ParticipationRequestDto> getUserRequests(Long userId) {
        log.info("Fetching participation requests for user={}", userId);

        if (!userRepository.existsById(userId)) {
            log.warn("User with id={} not found", userId);
            throw new NotFoundException("User with id=" + userId + " not found");
        }

        List<ParticipationRequestDto> requests = requestRepository.findAllByRequesterId(userId).stream()
                .map(requestMapper::toDto)
                .collect(Collectors.toList());

        log.info("Found {} participation requests for user={}", requests.size(), userId);
        return requests;
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        log.info("Canceling request id={} for user={}", requestId, userId);

        ParticipationRequest request = requestRepository.findByIdAndRequesterId(requestId, userId)
                .orElseThrow(() -> {
                    log.warn("Request with id={} for user={} not found", requestId, userId);
                    return new NotFoundException("Request with id=" + requestId + " for user=" + userId + " not found");
                });

        if (!request.getStatus().equals(RequestStatus.PENDING)) {
            log.warn("Request id={} is not pending, cannot cancel", requestId);
            throw new ConflictException("Only pending requests can be canceled");
        }

        request.setStatus(RequestStatus.CANCELED);
        ParticipationRequest updated = requestRepository.save(request);
        log.info("Request id={} canceled successfully", requestId);
        return requestMapper.toDto(updated);
    }

    @Override
    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        log.info("Fetching requests for event={} by initiator={}", eventId, userId);

        Event event = eventRepository.findByInitiatorIdAndId(userId, eventId)
                .orElseThrow(() -> {
                    log.warn("Event with id={} for user={} not found", eventId, userId);
                    return new NotFoundException("Event with id=" + eventId + " for user=" + userId + " not found");
                });

        List<ParticipationRequestDto> requests = requestRepository.findAllByEventId(eventId).stream()
                .map(requestMapper::toDto)
                .collect(Collectors.toList());

        log.info("Found {} requests for event={}", requests.size(), eventId);
        return requests;
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateRequestsStatus(Long userId, Long eventId, EventRequestStatusUpdateRequest updateRequest) {
        log.info("Updating request statuses for event={} by initiator={}. RequestIds: {}, new status: {}",
                eventId, userId, updateRequest.getRequestIds(), updateRequest.getStatus());

        Event event = eventRepository.findByInitiatorIdAndId(userId, eventId)
                .orElseThrow(() -> {
                    log.warn("Event with id={} for user={} not found", eventId, userId);
                    return new NotFoundException("Event with id=" + eventId + " for user=" + userId + " not found");
                });

        if (!event.getRequestModeration() || event.getParticipantLimit() == 0) {
            log.warn("Event={} does not require request moderation", eventId);
            throw new ConflictException("No moderation required for this event");
        }

        List<ParticipationRequest> requests = requestRepository.findAllByEventIdAndIdIn(eventId, updateRequest.getRequestIds());

        if (requests.stream().anyMatch(r -> !r.getStatus().equals(RequestStatus.PENDING))) {
            log.warn("Attempt to update non-pending requests: {}", updateRequest.getRequestIds());
            throw new ConflictException("Only pending requests can be moderated");
        }

        RequestStatus newStatus = RequestStatus.valueOf(updateRequest.getStatus());
        List<ParticipationRequestDto> confirmed = new ArrayList<>();
        List<ParticipationRequestDto> rejected = new ArrayList<>();

        if (newStatus == RequestStatus.CONFIRMED) {
            long currentConfirmed = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
            long available = event.getParticipantLimit() - currentConfirmed;

            if (available <= 0) {
                log.warn("Participant limit reached for event={}", eventId);
                throw new ConflictException("Participant limit reached");
            }

            int toConfirm = (int) Math.min(available, requests.size());

            for (int i = 0; i < toConfirm; i++) {
                ParticipationRequest req = requests.get(i);
                req.setStatus(RequestStatus.CONFIRMED);
                requestRepository.save(req);
                confirmed.add(requestMapper.toDto(req));
                log.info("Request id={} confirmed", req.getId());
            }

            // Reject remaining if any
            for (int i = toConfirm; i < requests.size(); i++) {
                ParticipationRequest req = requests.get(i);
                req.setStatus(RequestStatus.REJECTED);
                requestRepository.save(req);
                rejected.add(requestMapper.toDto(req));
                log.info("Request id={} rejected (excess after limit)", req.getId());
            }

            // Auto-reject all other pending if limit reached
            if (toConfirm < requests.size() || available == toConfirm) {
                List<ParticipationRequest> otherPending = requestRepository.findAllByEventId(eventId).stream()
                        .filter(r -> r.getStatus() == RequestStatus.PENDING && !updateRequest.getRequestIds().contains(r.getId()))
                        .toList();
                for (ParticipationRequest req : otherPending) {
                    req.setStatus(RequestStatus.REJECTED);
                    requestRepository.save(req);
                    rejected.add(requestMapper.toDto(req));
                    log.info("Request id={} auto-rejected due to limit", req.getId());
                }
            }

        } else if (newStatus == RequestStatus.REJECTED) {
            for (ParticipationRequest req : requests) {
                req.setStatus(RequestStatus.REJECTED);
                requestRepository.save(req);
                rejected.add(requestMapper.toDto(req));
                log.info("Request id={} rejected", req.getId());
            }
        }

        log.info("Request status update completed: {} confirmed, {} rejected", confirmed.size(), rejected.size());
        return new EventRequestStatusUpdateResult(confirmed, rejected);
    }
}