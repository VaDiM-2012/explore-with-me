// UpdateCompilationRequest.java
package ru.practicum.ewm.compilation.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCompilationRequest {

    @Size(min = 1, max = 50)
    private String title;

    private Boolean pinned;

    private Set<Long> events;
}