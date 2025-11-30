package ru.practicum.ewm.comment.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.comment.dto.CommentDto;
import ru.practicum.ewm.comment.service.CommentService;
import ru.practicum.stats.client.StatsClient;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/events/{eventId}/comments")
@RequiredArgsConstructor
@Slf4j
public class PublicCommentController {

    private static final String APP_NAME = "ewm-main-service";

    private final CommentService service;
    private final StatsClient statsClient;

    @GetMapping
    public List<CommentDto> getCommentsByEvent(@PathVariable Long eventId,
                                               @RequestParam(defaultValue = "0") Integer from,
                                               @RequestParam(defaultValue = "10") Integer size,
                                               HttpServletRequest request) {
        log.info("Получен публичный запрос GET /events/{}/comments — получение комментариев к событию: from={}, size={}", eventId, from, size);

        statsClient.hit(APP_NAME, request.getRequestURI(),
                request.getRemoteAddr(),
                LocalDateTime.now());

        List<CommentDto> comments = service.getCommentsByEvent(eventId, from, size);
        log.info("Возвращено {} комментариев к событию {}", comments.size(), eventId);
        return comments;
    }

    @GetMapping("/{commentId}")
    public CommentDto getCommentById(@PathVariable Long eventId,
                                     @PathVariable Long commentId,
                                     HttpServletRequest request) {
        log.info("Получен публичный запрос GET /events/{}/comments/{} — получение комментария по ID", eventId, commentId);

        statsClient.hit(APP_NAME, request.getRequestURI(),
                request.getRemoteAddr(),
                LocalDateTime.now());

        CommentDto comment = service.getCommentById(commentId);
        log.info("Комментарий найден: ID={}, text='{}'", comment.getId(), comment.getText());
        return comment;
    }
}