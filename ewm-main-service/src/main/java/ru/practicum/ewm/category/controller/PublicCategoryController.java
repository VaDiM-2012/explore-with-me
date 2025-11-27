package ru.practicum.ewm.category.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.category.dto.CategoryDto;
import ru.practicum.ewm.category.service.CategoryService;
import ru.practicum.stats.client.StatsClient; // Импорт клиента статистики
import jakarta.servlet.http.HttpServletRequest; // Импорт для доступа к данным запроса

import java.time.LocalDateTime; // Импорт для получения текущего времени

import java.util.List;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class PublicCategoryController {

    // Константа для имени сервиса при отправке статистики
    private static final String APP_NAME = "ewm-main-service";

    private final CategoryService service;
    private final StatsClient statsClient; // Инжекция клиента статистики

    @GetMapping
    public List<CategoryDto> getCategories(@RequestParam(defaultValue = "0") Integer from, @RequestParam(defaultValue = "10") Integer size, HttpServletRequest request) { // Добавление объекта запроса

        // Отправка информации о "хите" в сервис статистики

        statsClient.hit(APP_NAME, request.getRequestURI(),    // Получаем URI запроса
                request.getRemoteAddr(),    // Получаем IP-адрес клиента
                LocalDateTime.now()         // Текущее время
        );


        return service.getCategories(from, size);
    }

    @GetMapping("/{catId}")
    public CategoryDto getCategory(@PathVariable Long catId, HttpServletRequest request) { // Добавление объекта запроса

        // Отправка информации о "хите" в сервис статистики

        statsClient.hit(APP_NAME, request.getRequestURI(),    // Получаем URI запроса (включая catId)
                request.getRemoteAddr(),    // Получаем IP-адрес клиента
                LocalDateTime.now()         // Текущее время
        );


        return service.getCategoryById(catId);
    }

}