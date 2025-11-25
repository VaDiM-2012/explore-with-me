package ru.practicum.ewm.event.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.practicum.ewm.event.model.Location;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NewEventDto {

    @NotBlank
    @Size(min = 20, max = 2000)
    private String annotation;

    @NotNull
    private Long category;

    @NotBlank
    @Size(min = 20, max = 7000)
    private String description;

    @NotNull
    private String eventDate; // формат "yyyy-MM-dd HH:mm:ss"

    @NotNull
    private Location location;

    @NotNull
    private Boolean paid = false;

    @PositiveOrZero
    private Integer participantLimit = 0;

    @NotNull
    private Boolean requestModeration = true;

    @NotBlank
    @Size(min = 3, max = 120)
    private String title;
}