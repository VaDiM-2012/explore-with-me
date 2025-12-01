package ru.practicum.ewm.comment.mapper;

import org.springframework.stereotype.Component;
import ru.practicum.ewm.comment.dto.CommentDto;
import ru.practicum.ewm.comment.dto.NewCommentDto;
import ru.practicum.ewm.comment.dto.UpdateCommentDto;
import ru.practicum.ewm.comment.model.Comment;
import ru.practicum.ewm.event.mapper.EventMapper;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.user.mapper.UserMapper;
import ru.practicum.ewm.user.model.User;

import java.time.LocalDateTime;

@Component
public class CommentMapper {

    private final EventMapper eventMapper;
    private final UserMapper userMapper;

    public CommentMapper(EventMapper eventMapper, UserMapper userMapper) {
        this.eventMapper = eventMapper;
        this.userMapper = userMapper;
    }

    public Comment toEntity(NewCommentDto dto, User author, Event event) {
        return Comment.builder()
                .text(dto.getText())
                .author(author)
                .event(event)
                .createdOn(LocalDateTime.now())
                .build();
    }

    public CommentDto toDto(Comment comment) {
        return CommentDto.builder()
                .id(comment.getId())
                .text(comment.getText())
                .event(eventMapper.toShortDto(comment.getEvent()))
                .author(userMapper.toUserShortDto(comment.getAuthor()))
                .createdOn(comment.getCreatedOn())
                .build();
    }

    public void updateFromDto(UpdateCommentDto dto, Comment comment) {
        if (dto.getText() != null) {
            comment.setText(dto.getText());
        }
    }
}