package ru.practicum.ewm.compilation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.compilation.dto.CompilationDto;
import ru.practicum.ewm.compilation.dto.NewCompilationDto;
import ru.practicum.ewm.compilation.dto.UpdateCompilationRequest;
import ru.practicum.ewm.compilation.mapper.CompilationMapper;
import ru.practicum.ewm.compilation.model.Compilation;
import ru.practicum.ewm.compilation.repository.CompilationRepository;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.NotFoundException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class CompilationServiceImpl implements CompilationService {

    private final CompilationRepository compilationRepository;
    private final EventRepository eventRepository;
    private final CompilationMapper compilationMapper;

    @Override
    @Transactional
    public CompilationDto createCompilation(NewCompilationDto dto) {
        log.info("Начало создания подборки: pinned={}, title='{}', eventsCount={}",
                dto.isPinned(), dto.getTitle(), dto.getEvents() != null ? dto.getEvents().size() : 0);

        Set<Event> events = new HashSet<>();
        if (dto.getEvents() != null && !dto.getEvents().isEmpty()) {
            events = new HashSet<>(eventRepository.findAllById(dto.getEvents()));
            log.info("Найдено {} событий для добавления в подборку", events.size());
        }

        Compilation compilation = compilationMapper.toEntity(dto);
        compilation.setEvents(events);

        Compilation saved = compilationRepository.save(compilation);
        log.info("Подборка успешно создана с ID: {}", saved.getId());
        return compilationMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void deleteCompilation(Long compId) {
        log.info("Начало удаления подборки с ID: {}", compId);
        if (!compilationRepository.existsById(compId)) {
            log.warn("Подборка с ID {} не найдена при попытке удаления", compId);
            throw new NotFoundException("Compilation with id=" + compId + " not found");
        }
        compilationRepository.deleteById(compId);
        log.info("Подборка с ID {} успешно удалена", compId);
    }

    @Override
    @Transactional
    public CompilationDto updateCompilation(Long compId, UpdateCompilationRequest request) {
        log.info("Начало обновления подборки с ID: {}", compId);
        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> {
                    log.warn("Подборка с ID {} не найдена при обновлении", compId);
                    return new NotFoundException("Compilation with id=" + compId + " not found");
                });

        compilationMapper.updateFromRequest(request, compilation);

        if (request.getEvents() != null) {
            Set<Event> events = new HashSet<>(eventRepository.findAllById(request.getEvents()));
            compilation.setEvents(events);
            log.info("Обновлены события в подборке: {} событий добавлено", events.size());
        }

        Compilation updated = compilationRepository.save(compilation);
        log.info("Подборка с ID {} успешно обновлена", updated.getId());
        return compilationMapper.toDto(updated);
    }

    @Override
    public List<CompilationDto> getCompilations(Boolean pinned, Integer from, Integer size) {
        log.info("Получен запрос на получение подборок: pinned={}, from={}, size={}", pinned, from, size);
        PageRequest page = PageRequest.of(from / size, size, Sort.by("id").ascending());

        List<Compilation> compilations;
        if (pinned != null) {
            compilations = compilationRepository.findAllByPinned(pinned, page);
        } else {
            compilations = compilationRepository.findAllBy(page);
        }

        List<CompilationDto> result = compilations.stream()
                .map(compilationMapper::toDto)
                .collect(Collectors.toList());
        log.info("Найдено {} подборок", result.size());
        return result;
    }

    @Override
    public CompilationDto getCompilationById(Long compId) {
        log.info("Получен запрос на получение подборки по ID: {}", compId);
        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> {
                    log.warn("Подборка с ID {} не найдена", compId);
                    return new NotFoundException("Compilation with id=" + compId + " not found");
                });
        log.info("Подборка найдена: ID={}, title='{}'", compilation.getId(), compilation.getTitle());
        return compilationMapper.toDto(compilation);
    }
}