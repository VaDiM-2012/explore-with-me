package ru.practicum.ewm.event.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.State;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findAllByInitiatorId(Long userId, Pageable pageable);

    Optional<Event> findByInitiatorIdAndId(Long userId, Long eventId);

    @Query(value = """
    SELECT e.* FROM events e
    WHERE (:users      IS NULL OR e.initiator_id IN (SELECT UNNEST(CAST(:users      AS BIGINT[]))))
      AND (:states     IS NULL OR e.state::text     IN (SELECT UNNEST(CAST(:states     AS TEXT[]))))
      AND (:categories IS NULL OR e.category_id     IN (SELECT UNNEST(CAST(:categories AS BIGINT[]))))
      AND (:rangeStart IS NULL OR e.event_date >= :rangeStart)
      AND (:rangeEnd   IS NULL OR e.event_date <= :rangeEnd)
    ORDER BY e.id ASC
    """, nativeQuery = true)
    List<Event> findAllByAdminFilters(
            @Param("users")      List<Long> users,
            @Param("states")     List<String> states,
            @Param("categories") List<Long> categories,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd")   LocalDateTime rangeEnd,
            Pageable pageable);

    // === Public API: поиск опубликованных событий ===
    @Query("""
            SELECT e FROM Event e
            WHERE e.state = 'PUBLISHED'
              AND (:text IS NULL OR
                   LOWER(e.annotation) LIKE LOWER(CONCAT('%', :text, '%')) OR
                   LOWER(e.description) LIKE LOWER(CONCAT('%', :text, '%')))
              AND (:categories IS NULL OR e.category.id IN :categories)
              AND (:paid IS NULL OR e.paid = :paid)
              AND e.eventDate >= :start
              AND e.eventDate <= :end
            ORDER BY e.eventDate ASC
            """)
    List<Event> findPublicEvents(
            @Param("text") String text,
            @Param("categories") List<Long> categories,
            @Param("paid") Boolean paid,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);

    // === Public API: получение одного опубликованного события ===
    @Query("SELECT e FROM Event e WHERE e.id = :id AND e.state = :state")
    Optional<Event> findByIdAndState(@Param("id") Long id, @Param("state") State state);
}