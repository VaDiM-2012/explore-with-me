package ru.practicum.ewm.comment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.comment.dto.CommentDto;
import ru.practicum.ewm.comment.dto.NewCommentDto;
import ru.practicum.ewm.comment.dto.UpdateCommentDto;
import ru.practicum.ewm.comment.mapper.CommentMapper;
import ru.practicum.ewm.comment.model.Comment;
import ru.practicum.ewm.comment.repository.CommentRepository;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.State;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final CommentMapper commentMapper;

    @Override
    @Transactional
    public CommentDto addComment(Long userId, Long eventId, NewCommentDto dto) {
        log.info("Начало добавления комментария к событию {} от пользователя {}", eventId, userId);

        User author = getUserOrThrow(userId);
        Event event = getEventOrThrow(eventId);

        if (event.getState() != State.PUBLISHED) {
            log.warn("Попытка добавить комментарий к неопубликованному событию {}", eventId);
            throw new ConflictException("Комментарии можно добавлять только к опубликованным событиям");
        }

        Comment comment = commentMapper.toEntity(dto, author, event);
        Comment saved = commentRepository.save(comment);
        log.info("Комментарий успешно добавлен с ID: {}", saved.getId());

        return commentMapper.toDto(saved);
    }

    @Override
    @Transactional
    public CommentDto updateComment(Long userId, Long commentId, UpdateCommentDto dto) {
        log.info("Начало обновления комментария {} от пользователя {}", commentId, userId);

        Comment comment = getCommentOrThrow(commentId);

        if (!comment.getAuthor().getId().equals(userId)) {
            log.warn("Попытка обновить чужой комментарий {} пользователем {}", commentId, userId);
            throw new ConflictException("Обновлять можно только свои комментарии");
        }

        commentMapper.updateFromDto(dto, comment);
        Comment updated = commentRepository.save(comment);
        log.info("Комментарий с ID {} успешно обновлён", updated.getId());

        return commentMapper.toDto(updated);
    }

    @Override
    @Transactional
    public void deleteCommentByUser(Long userId, Long commentId) {
        log.info("Начало удаления комментария {} пользователем {}", commentId, userId);

        Comment comment = getCommentOrThrow(commentId);

        if (!comment.getAuthor().getId().equals(userId)) {
            log.warn("Попытка удалить чужой комментарий {} пользователем {}", commentId, userId);
            throw new ConflictException("Удалять можно только свои комментарии");
        }

        commentRepository.deleteById(commentId);
        log.info("Комментарий с ID {} успешно удалён пользователем {}", commentId, userId);
    }

    @Override
    @Transactional
    public void deleteCommentByAdmin(Long commentId) {
        log.info("Начало удаления комментария {} администратором", commentId);

        getCommentOrThrow(commentId);

        commentRepository.deleteById(commentId);
        log.info("Комментарий с ID {} успешно удалён администратором", commentId);
    }

    @Override
    public List<CommentDto> getCommentsByEvent(Long eventId, Integer from, Integer size) {
        log.info("Получен запрос на получение комментариев к событию {}: from={}, size={}", eventId, from, size);

        getEventOrThrow(eventId);

        int page = from / size;
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdOn").descending());

        List<CommentDto> comments = commentRepository.findAllByEventId(eventId, pageRequest).stream()
                .map(commentMapper::toDto)
                .collect(Collectors.toList());

        log.info("Возвращено {} комментариев к событию {}", comments.size(), eventId);
        return comments;
    }

    @Override
    public List<CommentDto> getCommentsByUser(Long userId, Integer from, Integer size) {
        log.info("Получен запрос на получение комментариев пользователя {}: from={}, size={}", userId, from, size);

        getUserOrThrow(userId);

        int page = from / size;
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdOn").descending());

        List<CommentDto> comments = commentRepository.findAllByAuthorId(userId, pageRequest).stream()
                .map(commentMapper::toDto)
                .collect(Collectors.toList());

        log.info("Возвращено {} комментариев пользователя {}", comments.size(), userId);
        return comments;
    }

    @Override
    public CommentDto getCommentById(Long commentId) {
        log.info("Получен запрос на получение комментария по ID: {}", commentId);

        Comment comment = getCommentOrThrow(commentId);
        log.info("Комментарий найден: ID={}, text='{}'", comment.getId(), comment.getText());

        return commentMapper.toDto(comment);
    }

    private Comment getCommentOrThrow(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> {
                    log.warn("Комментарий с ID {} не найден", commentId);
                    return new NotFoundException("Комментарий с id=" + commentId + " не найден");
                });
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Пользователь с ID {} не найден", userId);
                    return new NotFoundException("Пользователь с id=" + userId + " не найден");
                });
    }

    private Event getEventOrThrow(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    log.warn("Событие с ID {} не найдено", eventId);
                    return new NotFoundException("Событие с id=" + eventId + " не найдено");
                });
    }
}