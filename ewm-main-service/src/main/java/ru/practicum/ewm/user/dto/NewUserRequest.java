package ru.practicum.ewm.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NewUserRequest {

    @NotBlank(message = "Email не может быть пустым")
    @Email(message = "Email должен быть корректным адресом электронной почты")
    @Size(min = 6, max = 254, message = "Email должен содержать от 6 до 254 символов")
    private String email;

    @NotBlank(message = "Имя не может быть пустым")
    @Size(min = 2, max = 250, message = "Имя должно содержать от 2 до 250 символов")
    private String name;
}