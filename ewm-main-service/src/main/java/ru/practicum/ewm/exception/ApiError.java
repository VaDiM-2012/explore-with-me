package ru.practicum.ewm.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiError {
    /**
     * Список стектрейсов или описания ошибок
     */
    private List<String> errors;

    /**
     * Сообщение об ошибке
     */
    private String message;

    /**
     * Общее описание причины ошибки
     */
    private String reason;

    /**
     * Код статуса HTTP-ответа
     */
    private String status;

    /**
     * Дата и время когда произошла ошибка (в формате "yyyy-MM-dd HH:mm:ss")
     */
    private String timestamp;
}