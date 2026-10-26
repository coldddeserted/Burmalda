package com.burmalda.web.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/** Тела входящих запросов. Валидация через @Valid в контроллерах. */
public final class Requests {

    private Requests() {
    }

    public record Login(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record Register(
            @NotBlank String name,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, message = "минимум 6 символов") String password,
            String role,
            String department,
            String company) {
    }

    public record Amount(@NotNull @Positive BigDecimal amount) {
    }

    public record TaskStatus(@NotBlank String status) {
    }

    /** seconds — сколько всего натикало в трекере за сегодня. */
    public record TimeLog(@PositiveOrZero long seconds, String label) {
    }

    public record AddMember(@NotBlank String name, @Email String email, String task) {
    }

    public record Pay(@NotEmpty List<Long> ids) {
    }

    public record Payment(@NotBlank String to, @NotNull @Positive BigDecimal amount, String note) {
    }

    public record Report(String format, String period) {
    }

    public record Chat(@NotBlank @Size(max = 2000) String message, String role) {
    }
}
