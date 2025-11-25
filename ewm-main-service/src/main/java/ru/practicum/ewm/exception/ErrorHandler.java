package ru.practicum.ewm.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class ErrorHandler {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Обработка ошибки 404 (Not Found).
     */
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNotFoundException(final NotFoundException e) {
        log.warn("404 Not Found: {}", e.getMessage());
        return buildApiError(
                HttpStatus.NOT_FOUND,
                "The required object was not found.",
                e.getMessage(),
                Collections.emptyList()
        );
    }

    /**
     * Обработка ошибки 409 (Conflict).
     * Включает кастомные бизнес-конфликты и ошибки целостности БД (например, уникальные индексы).
     */
    @ExceptionHandler({ConflictException.class, DataIntegrityViolationException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleConflictException(final RuntimeException e) {
        log.warn("409 Conflict: {}", e.getMessage());
        return buildApiError(
                HttpStatus.CONFLICT,
                "Integrity constraint has been violated.",
                e.getMessage(),
                Collections.singletonList(getStackTraceAsString(e))
        );
    }

    /**
     * Обработка ошибки 400 (Bad Request).
     * Включает ошибки валидации (@Valid), отсутствие параметров и кастомные ошибки валидации.
     */
    @ExceptionHandler({
            ValidationException.class,
            MethodArgumentNotValidException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleBadRequestException(final Exception e) {
        log.warn("400 Bad Request: {}", e.getMessage());
        
        String message = e.getMessage();
        List<String> errors = Collections.singletonList(getStackTraceAsString(e));

        // Если это ошибка валидации полей, формируем более детальное сообщение
        if (e instanceof MethodArgumentNotValidException) {
            message = "Validation failed";
            errors = ((MethodArgumentNotValidException) e).getBindingResult().getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.toList());
        }

        return buildApiError(
                HttpStatus.BAD_REQUEST,
                "Incorrectly made request.",
                message,
                errors
        );
    }

    /**
     * Обработка всех остальных исключений (500 Internal Server Error).
     */
    @ExceptionHandler
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleThrowable(final Throwable e) {
        log.error("500 Internal Server Error", e);
        return buildApiError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                e.getMessage(),
                Collections.singletonList(getStackTraceAsString(e))
        );
    }

    private ApiError buildApiError(HttpStatus status, String reason, String message, List<String> errors) {
        return ApiError.builder()
                .status(status.name())
                .reason(reason)
                .message(message)
                .errors(errors)
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();
    }

    private String getStackTraceAsString(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}