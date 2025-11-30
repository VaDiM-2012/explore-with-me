package ru.practicum.ewm.category.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.category.dto.CategoryDto;
import ru.practicum.ewm.category.dto.NewCategoryDto;
import ru.practicum.ewm.category.service.CategoryService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AdminCategoryController {

    private final CategoryService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto addCategory(@Valid @RequestBody NewCategoryDto dto) {
        log.info("Получен запрос POST /admin/categories — добавление новой категории: {}", dto);
        CategoryDto result = service.addCategory(dto);
        log.info("Категория успешно добавлена с ID: {}", result.getId());
        return result;
    }

    @PatchMapping("/{catId}")
    public CategoryDto updateCategory(@PathVariable Long catId,
                                      @Valid @RequestBody CategoryDto dto) {
        log.info("Получен запрос PATCH /admin/categories/{} — обновление категории: {}", catId, dto);
        CategoryDto result = service.updateCategory(catId, dto);
        log.info("Категория с ID {} успешно обновлена", result.getId());
        return result;
    }

    @DeleteMapping("/{catId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long catId) {
        log.info("Получен запрос DELETE /admin/categories/{} — удаление категории", catId);
        service.deleteCategory(catId);
        log.info("Категория с ID {} успешно удалена", catId);
    }
}