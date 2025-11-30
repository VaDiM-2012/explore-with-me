package ru.practicum.ewm.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.user.dto.NewUserRequest;
import ru.practicum.ewm.user.dto.UserDto;
import ru.practicum.ewm.user.mapper.UserMapper;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class UserServiceImpl implements UserService {

     private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public UserDto createUser(NewUserRequest request) {
        log.info("Начало создания пользователя с email={}", request.getEmail());
        User user = userMapper.toUser(request);
        try {
            User saved = userRepository.save(user);
            log.info("Пользователь успешно создан с id={}", saved.getId());
            return userMapper.toUserDto(saved);
        } catch (DataIntegrityViolationException e) {
            log.warn("Попытка создания пользователя с уже существующим email={}", request.getEmail());
            throw new ConflictException("Пользователь с email = " + request.getEmail() + " уже существует");
        }
    }

    @Override
    public List<UserDto> getUsers(List<Long> ids, Integer from, Integer size) {
        log.info("Получение списка пользователей с параметрами: ids={}, from={}, size={}", ids, from, size);

        PageRequest pageRequest = PageRequest.of(from / size, size, Sort.by("id").ascending());

        List<User> users;
        if (ids == null || ids.isEmpty()) {
            users = userRepository.findAll(pageRequest).getContent();
            log.info("Найдено {} пользователей без фильтрации по id", users.size());
        } else {
            users = userRepository.findAllById(ids);
            log.info("Найдено {} пользователей по списку id", users.size());

            users = users.stream()
                    .skip(from)
                    .limit(size)
                    .collect(Collectors.toList());
            log.info("После пагинации осталось {} пользователей", users.size());
        }

        List<UserDto> dtos = users.stream()
                .map(userMapper::toUserDto)
                .collect(Collectors.toList());

        log.info("Возвращено {} пользователей в виде DTO", dtos.size());
        return dtos;
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        log.info("Начало удаления пользователя с id={}", userId);

        if (!userRepository.existsById(userId)) {
            log.warn("Попытка удалить несуществующего пользователя с id={}", userId);
            throw new NotFoundException("User with id=" + userId + " was not found");
        }

        userRepository.deleteById(userId);
        log.info("Пользователь с id={} успешно удалён", userId);
    }
}