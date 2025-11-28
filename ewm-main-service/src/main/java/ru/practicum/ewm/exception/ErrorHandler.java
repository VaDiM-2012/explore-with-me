package ru.practicum.ewm.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
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

/**
 * Глобальный обработчик исключений для REST контроллеров.
 * Перехватывает различные типы исключений, конвертирует их в стандартизированный формат
 * {@link ApiError} и возвращает соответствующий HTTP-статус.
 */
@RestControllerAdvice
@Slf4j
public class ErrorHandler {

    /**
     * Форматтер для отображения временной метки в ответе API.
     */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Обработка ошибки 400 (Bad Request).
     * Перехватывает:
     * <ul>
     * <li>{@link ValidationException}: Кастомные ошибки бизнес-логики, связанные с неверными данными.</li>
     * <li>{@link MethodArgumentNotValidException}: Ошибки валидации полей (например, по аннотациям @Valid).</li>
     * <li>{@link MissingServletRequestParameterException}: Отсутствие обязательных параметров запроса.</li>
     * <li>{@link MethodArgumentTypeMismatchException}: Неправильный тип аргумента (например, строка вместо числа).</li>
     * </ul>
     * @param e Перехваченное исключение.
     * @return Объект {@link ApiError} с подробностями ошибки.
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
            message = "Validation failed for request arguments.";
            errors = ((MethodArgumentNotValidException) e).getBindingResult().getFieldErrors().stream()
                    .map(error -> String.format("Field: %s. Error: %s. Value: %s",
                            error.getField(), error.getDefaultMessage(), error.getRejectedValue()))
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
     * Обработка ошибки 404 (Not Found).
     * Перехватывает:
     * <ul>
     * <li>{@link NotFoundException}: Кастомные ошибки, когда объект не найден в системе.</li>
     * </ul>
     * @param e Перехваченное исключение.
     * @return Объект {@link ApiError} с подробностями ошибки.
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
     * Перехватывает:
     * <ul>
     * <li>{@link ConflictException}: Кастомные ошибки бизнес-логики, связанные с нарушением условий (например, нельзя отменить опубликованное событие).</li>
     * <li>{@link DataIntegrityViolationException}: Ошибки целостности базы данных (например, нарушение уникального индекса).</li>
     * </ul>
     * @param e Перехваченное исключение.
     * @return Объект {@link ApiError} с подробностями ошибки.
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
     * Обработка всех остальных исключений (500 Internal Server Error).
     * @param e Любое неперехваченное исключение.
     * @return Объект {@link ApiError} с подробностями ошибки и трассировкой стека.
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

    /**
     * Вспомогательный метод для создания стандартизированного объекта {@link ApiError}.
     * @param status HTTP-статус.
     * @param reason Общее описание причины ошибки.
     * @param message Детальное сообщение об ошибке.
     * @param errors Список ошибок или трассировка стека.
     * @return Созданный объект {@link ApiError}.
     */
    private ApiError buildApiError(HttpStatus status, String reason, String message, List<String> errors) {
        return ApiError.builder()
                .status(status.name())
                .reason(reason)
                .message(message)
                .errors(errors)
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();
    }

    /**
     * Вспомогательный метод для получения трассировки стека в виде строки.
     * @param e Исключение.
     * @return Трассировка стека в виде строки.
     */
    private String getStackTraceAsString(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}