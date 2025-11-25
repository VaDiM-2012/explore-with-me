package ru.practicum.ewm.category.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.practicum.ewm.category.model.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByName(String name);

    // Проверка на наличие событий (предполагаем, что Entity Event существует в пакете event.model.Event)
    @Query("SELECT COUNT(e) > 0 FROM Event e WHERE e.category.id = :catId")
    boolean hasEvents(Long catId);
}