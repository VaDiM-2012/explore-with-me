package ru.practicum.ewm.compilation.service;

import ru.practicum.ewm.compilation.dto.CompilationDto;
import ru.practicum.ewm.compilation.dto.NewCompilationDto;
import ru.practicum.ewm.compilation.dto.UpdateCompilationRequest;

import java.util.List;

public interface CompilationService {

    // Admin
    CompilationDto createCompilation(NewCompilationDto dto);
    void deleteCompilation(Long compId);
    CompilationDto updateCompilation(Long compId, UpdateCompilationRequest request);

    // Public
    List<CompilationDto> getCompilations(Boolean pinned, Integer from, Integer size);
    CompilationDto getCompilationById(Long compId);
}