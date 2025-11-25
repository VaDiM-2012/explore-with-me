package ru.practicum.ewm.request.service;

import lombok.RequiredArgsConstructor;
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

    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final RequestMapper requestMapper;

    @Override
    @Transactional
    public ParticipationRequestDto createRequest(Long userId, Long eventId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " not found"));

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " not found"));

        if (event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("Initiator cannot request participation in own event");
        }

        if (!event.getState().equals(State.PUBLISHED)) {
            throw new ConflictException("Cannot participate in unpublished event");
        }

        if (requestRepository.existsByRequesterIdAndEventId(userId, eventId)) {
            throw new ConflictException("Duplicate participation request");
        }

        long confirmed = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        if (event.getParticipantLimit() > 0 && confirmed >= event.getParticipantLimit()) {
            throw new ConflictException("Participant limit reached");
        }

        RequestStatus status = (event.getParticipantLimit() == 0 || !event.getRequestModeration())
                ? RequestStatus.CONFIRMED : RequestStatus.PENDING;

        ParticipationRequest request = ParticipationRequest.builder()
                .requester(user)
                .event(event)
                .status(status)
                .build();

        ParticipationRequest saved = requestRepository.save(request);
        return requestMapper.toDto(saved);
    }

    @Override
    public List<ParticipationRequestDto> getUserRequests(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User with id=" + userId + " not found");
        }
        return requestRepository.findAllByRequesterId(userId).stream()
                .map(requestMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        ParticipationRequest request = requestRepository.findByIdAndRequesterId(requestId, userId)
                .orElseThrow(() -> new NotFoundException("Request with id=" + requestId + " for user=" + userId + " not found"));

        if (!request.getStatus().equals(RequestStatus.PENDING)) {
            throw new ConflictException("Only pending requests can be canceled");
        }

        request.setStatus(RequestStatus.CANCELED);
        ParticipationRequest updated = requestRepository.save(request);
        return requestMapper.toDto(updated);
    }

    @Override
    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        Event event = eventRepository.findByInitiatorIdAndId(userId, eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " for user=" + userId + " not found"));

        return requestRepository.findAllByEventId(eventId).stream()
                .map(requestMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateRequestsStatus(Long userId, Long eventId, EventRequestStatusUpdateRequest updateRequest) {
        Event event = eventRepository.findByInitiatorIdAndId(userId, eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " for user=" + userId + " not found"));

        if (!event.getRequestModeration() || event.getParticipantLimit() == 0) {
            throw new ConflictException("No moderation required for this event");
        }

        List<ParticipationRequest> requests = requestRepository.findAllByEventIdAndIdIn(eventId, updateRequest.getRequestIds());

        if (requests.stream().anyMatch(r -> !r.getStatus().equals(RequestStatus.PENDING))) {
            throw new ConflictException("Only pending requests can be moderated");
        }

        RequestStatus newStatus = RequestStatus.valueOf(updateRequest.getStatus());
        List<ParticipationRequestDto> confirmed = new ArrayList<>();
        List<ParticipationRequestDto> rejected = new ArrayList<>();

        if (newStatus == RequestStatus.CONFIRMED) {
            long currentConfirmed = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
            long available = event.getParticipantLimit() - currentConfirmed;

            if (available <= 0) {
                throw new ConflictException("Participant limit reached");
            }

            int toConfirm = (int) Math.min(available, requests.size());

            for (int i = 0; i < toConfirm; i++) {
                ParticipationRequest req = requests.get(i);
                req.setStatus(RequestStatus.CONFIRMED);
                requestRepository.save(req);
                confirmed.add(requestMapper.toDto(req));
            }

            // Reject remaining if any
            for (int i = toConfirm; i < requests.size(); i++) {
                ParticipationRequest req = requests.get(i);
                req.setStatus(RequestStatus.REJECTED);
                requestRepository.save(req);
                rejected.add(requestMapper.toDto(req));
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
                }
            }

        } else if (newStatus == RequestStatus.REJECTED) {
            for (ParticipationRequest req : requests) {
                req.setStatus(RequestStatus.REJECTED);
                requestRepository.save(req);
                rejected.add(requestMapper.toDto(req));
            }
        }

        return new EventRequestStatusUpdateResult(confirmed, rejected);
    }
}