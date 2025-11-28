package ru.practicum.ewm.compilation.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.compilation.dto.CompilationDto;
import ru.practicum.ewm.compilation.service.CompilationService;
import ru.practicum.stats.client.StatsClient;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
@RestController
@RequestMapping("/compilations")
@RequiredArgsConstructor
@Slf4j
public class PublicCompilationController {

    private static final String APP_NAME = "ewm-main-service";

    private final CompilationService service;
    private final StatsClient statsClient;

    @GetMapping
    public List<CompilationDto> getCompilations(
            @RequestParam(required = false) Boolean pinned,
            @RequestParam(defaultValue = "0") Integer from,
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request
    ) {
        log.info("Получен публичный запрос GET /compilations — получение подборок: pinned={}, from={}, size={}", pinned, from, size);

        statsClient.hit(
                APP_NAME,
                request.getRequestURI(),
                request.getRemoteAddr(),
                LocalDateTime.now()
        );

        List<CompilationDto> compilations = service.getCompilations(pinned, from, size);
        log.info("Найдено {} подборок", compilations.size());
        return compilations;
    }

    @GetMapping("/{compId}")
    public CompilationDto getCompilationById(
            @PathVariable Long compId,
            HttpServletRequest request
    ) {
        log.info("Получен публичный запрос GET /compilations/{} — получение подборки по ID", compId);

        statsClient.hit(
                APP_NAME,
                request.getRequestURI(),
                request.getRemoteAddr(),
                LocalDateTime.now()
        );

        CompilationDto compilation = service.getCompilationById(compId);
        log.info("Подборка найдена: ID={}, title='{}'", compilation.getId(), compilation.getTitle());
        return compilation;
    }
}