package ru.practicum.ewm.comment.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.comment.dto.CommentDto;
import ru.practicum.ewm.comment.dto.NewCommentDto;
import ru.practicum.ewm.comment.dto.UpdateCommentDto;
import ru.practicum.ewm.comment.service.CommentService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

@RestController
@RequestMapping("/users/{userId}/comments")
@RequiredArgsConstructor
@Validated
@Slf4j
public class PrivateCommentController {

    private final CommentService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentDto addComment(@PathVariable Long userId,
                                 @RequestParam Long eventId,
                                 @Valid @RequestBody NewCommentDto dto) {
        log.info("Получен запрос POST /users/{}/comments?eventId={} — добавление комментария: {}", userId, eventId, dto);
        CommentDto result = service.addComment(userId, eventId, dto);
        log.info("Комментарий успешно добавлен с ID: {}", result.getId());
        return result;
    }

    @PatchMapping("/{commentId}")
    public CommentDto updateComment(@PathVariable Long userId,
                                    @PathVariable Long commentId,
                                    @Valid @RequestBody UpdateCommentDto dto) {
        log.info("Получен запрос PATCH /users/{}/comments/{} — обновление комментария: {}", userId, commentId, dto);
        CommentDto result = service.updateComment(userId, commentId, dto);
        log.info("Комментарий с ID {} успешно обновлён", result.getId());
        return result;
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable Long userId,
                              @PathVariable Long commentId) {
        log.info("Получен запрос DELETE /users/{}/comments/{} — удаление комментария", userId, commentId);
        service.deleteCommentByUser(userId, commentId);
        log.info("Комментарий с ID {} успешно удалён пользователем {}", commentId, userId);
    }

    @GetMapping
    public List<CommentDto> getMyComments(@PathVariable Long userId,
                                          @RequestParam(defaultValue = "0") @PositiveOrZero Integer from,
                                          @RequestParam(defaultValue = "10") @Positive Integer size) {
        log.info("Получен запрос GET /users/{}/comments — получение комментариев пользователя: from={}, size={}", userId, from, size);
        List<CommentDto> comments = service.getCommentsByUser(userId, from, size);
        log.info("Возвращено {} комментариев пользователя {}", comments.size(), userId);
        return comments;
    }
}