package ru.practicum.ewm.compilation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.compilation.dto.CompilationDto;
import ru.practicum.ewm.compilation.dto.NewCompilationDto;
import ru.practicum.ewm.compilation.dto.UpdateCompilationRequest;
import ru.practicum.ewm.compilation.service.CompilationService;

@RestController
@RequestMapping("/admin/compilations")
@RequiredArgsConstructor
@Slf4j
public class AdminCompilationController {

    private final CompilationService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompilationDto createCompilation(@Valid @RequestBody NewCompilationDto dto) {
        log.info("Получен запрос POST /admin/compilations — создание новой подборки: pinned={}, title={}", dto.isPinned(), dto.getTitle());
        CompilationDto result = service.createCompilation(dto);
        log.info("Подборка успешно создана с ID: {}", result.getId());
        return result;
    }

    @DeleteMapping("/{compId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCompilation(@PathVariable Long compId) {
        log.info("Получен запрос DELETE /admin/compilations/{} — удаление подборки", compId);
        service.deleteCompilation(compId);
        log.info("Подборка с ID {} успешно удалена", compId);
    }

    @PatchMapping("/{compId}")
    public CompilationDto updateCompilation(@PathVariable Long compId,
                                             @Valid @RequestBody UpdateCompilationRequest request) {
        log.info("Получен запрос PATCH /admin/compilations/{} — обновление подборки: {}", compId, request);
        CompilationDto result = service.updateCompilation(compId, request);
        log.info("Подборка с ID {} успешно обновлена", result.getId());
        return result;
    }
}