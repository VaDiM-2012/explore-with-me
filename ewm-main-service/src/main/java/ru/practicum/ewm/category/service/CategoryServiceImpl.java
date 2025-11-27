package ru.practicum.ewm.category.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.category.dto.CategoryDto;
import ru.practicum.ewm.category.dto.NewCategoryDto;
import ru.practicum.ewm.category.mapper.CategoryMapper;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.repository.CategoryRepository;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryServiceImpl.class);

    private final CategoryRepository repository;
    private final CategoryMapper mapper;

    @Override
    @Transactional
    public CategoryDto addCategory(NewCategoryDto dto) {
        log.info("Начало добавления новой категории: {}", dto.getName());
        if (repository.existsByName(dto.getName())) {
            log.warn("Категория с именем '{}' уже существует", dto.getName());
            throw new ConflictException("Категория с именем '" + dto.getName() + "' уже существует");
        }
        Category saved = repository.save(mapper.toCategory(dto));
        log.info("Категория успешно добавлена с ID: {}", saved.getId());
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public CategoryDto updateCategory(Long catId, CategoryDto dto) {
        log.info("Начало обновления категории с ID: {}", catId);
        Category category = repository.findById(catId)
                .orElseThrow(() -> {
                    log.warn("Категория с ID {} не найдена при попытке обновления", catId);
                    return new NotFoundException("Категория с id=" + catId + " не найдена");
                });

        String newName = dto.getName();
        if (newName != null && !newName.equals(category.getName()) && repository.existsByName(newName)) {
            log.warn("Категория с именем '{}' уже существует при обновлении", newName);
            throw new ConflictException("Категория с именем '" + newName + "' уже существует");
        }

        mapper.updateFromDto(dto, category);
        Category updated = repository.save(category);
        log.info("Категория с ID {} успешно обновлена", updated.getId());
        return mapper.toDto(updated);
    }

    @Override
    @Transactional
    public void deleteCategory(Long catId) {
        log.info("Начало удаления категории с ID: {}", catId);
        Category category = repository.findById(catId)
                .orElseThrow(() -> {
                    log.warn("Категория с ID {} не найдена при попытке удаления", catId);
                    return new NotFoundException("Категория с id=" + catId + " не найдена");
                });

        if (repository.hasEvents(catId)) {
            log.warn("Попытка удалить категорию с ID {}, к которой привязаны события", catId);
            throw new ConflictException("Нельзя удалить категорию, к которой привязаны события");
        }

        repository.deleteById(catId);
        log.info("Категория с ID {} успешно удалена", catId);
    }

    @Override
    public List<CategoryDto> getCategories(Integer from, Integer size) {
        log.info("Получен запрос на получение списка категорий: from={}, size={}", from, size);
        int page = from == 0 ? 0 : from / size;
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("id").ascending());

        List<CategoryDto> categories = repository.findAll(pageRequest).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
        log.info("Возвращено {} категорий", categories.size());
        return categories;
    }

    @Override
    public CategoryDto getCategoryById(Long catId) {
        log.info("Получен запрос на получение категории по ID: {}", catId);
        Category category = repository.findById(catId)
                .orElseThrow(() -> {
                    log.warn("Категория с ID {} не найдена", catId);
                    return new NotFoundException("Категория с id=" + catId + " не найдена");
                });
        log.info("Категория найдена: ID={}, name='{}'", category.getId(), category.getName());
        return mapper.toDto(category);
    }
}