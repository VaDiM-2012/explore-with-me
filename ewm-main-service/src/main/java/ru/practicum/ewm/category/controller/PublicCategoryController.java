package ru.practicum.ewm.category.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.category.dto.CategoryDto;
import ru.practicum.ewm.category.service.CategoryService;
import ru.practicum.stats.client.StatsClient;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
@Slf4j
public class PublicCategoryController {

    private static final String APP_NAME = "ewm-main-service";

    private final CategoryService service;
    private final StatsClient statsClient;

    @GetMapping
    public List<CategoryDto> getCategories(@RequestParam(defaultValue = "0") Integer from,
                                           @RequestParam(defaultValue = "10") Integer size,
                                           HttpServletRequest request) {

        log.info("Получен публичный запрос GET /categories — получение списка категорий: from={}, size={}", from, size);

        statsClient.hit(APP_NAME, request.getRequestURI(),
                request.getRemoteAddr(),
                LocalDateTime.now());

        List<CategoryDto> categories = service.getCategories(from, size);
        log.info("Возвращено {} категорий", categories.size());
        return categories;
    }

    @GetMapping("/{catId}")
    public CategoryDto getCategory(@PathVariable Long catId, HttpServletRequest request) {

        log.info("Получен публичный запрос GET /categories/{} — получение категории по ID", catId);

        statsClient.hit(APP_NAME, request.getRequestURI(),
                request.getRemoteAddr(),
                LocalDateTime.now());

        CategoryDto category = service.getCategoryById(catId);
        log.info("Категория найдена: ID={}, name='{}'", category.getId(), category.getName());
        return category;
    }
}