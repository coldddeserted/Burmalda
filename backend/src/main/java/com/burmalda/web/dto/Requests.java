package com.burmalda.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/** Request bodies. Validated with @Valid in the controllers. */
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
            @NotBlank @Size(min = 6, message = "must be at least 6 characters") String password,
            String role,
            String department,
            String company) {
    }

    public record Amount(@NotNull @Positive BigDecimal amount) {
    }

    public record TaskStatus(@NotBlank String status) {
    }

    /** seconds — the tracker's total for today. */
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

    /** message may be empty when files are attached. */
    public record Chat(@Size(max = 2000) String message, String role,
                       @Size(max = 3, message = "at most 3 files") List<@Valid Attachment> attachments) {
    }

    /** A photo or file attached in the AI chat. data = base64 without the "data:...;base64," prefix. */
    public record Attachment(@NotBlank @Size(max = 200) String name,
                             @NotBlank String type,
                             @NotBlank @Size(max = 7_000_000, message = "file is too large (max 5 MB)") String data) {
    }
}
