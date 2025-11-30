package ru.practicum.ewm.request.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.request.dto.ParticipationRequestDto;
import ru.practicum.ewm.request.service.RequestService;
import java.util.List;

@RestController
@RequestMapping("/users/{userId}/requests")
@RequiredArgsConstructor
@Slf4j
public class PrivateRequestController {

    private final RequestService requestService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ParticipationRequestDto createRequest(@PathVariable Long userId,
                                                 @RequestParam Long eventId) {
        log.info("Получен запрос POST /users/{}/requests — создание заявки на участие в событии eventId={}", userId, eventId);
        ParticipationRequestDto requestDto = requestService.createRequest(userId, eventId);
        log.info("Заявка успешно создана: requestId={}, status={}", requestDto.getId(), requestDto.getStatus());
        return requestDto;
    }

    @GetMapping
    public List<ParticipationRequestDto> getUserRequests(@PathVariable Long userId) {
        log.info("Получен запрос GET /users/{}/requests — получение списка заявок пользователя", userId);
        List<ParticipationRequestDto> requests = requestService.getUserRequests(userId);
        log.info("Пользователь {} имеет {} заявок на участие", userId, requests.size());
        return requests;
    }

    @PatchMapping("/{requestId}/cancel")
    public ParticipationRequestDto cancelRequest(@PathVariable Long userId,
                                                 @PathVariable Long requestId) {
        log.info("Получен запрос PATCH /users/{}/requests/{}/cancel — отмена заявки пользователем", userId, requestId);
        ParticipationRequestDto canceled = requestService.cancelRequest(userId, requestId);
        log.info("Заявка с ID {} успешно отменена, новый статус: {}", canceled.getId(), canceled.getStatus());
        return canceled;
    }
}