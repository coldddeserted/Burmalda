package com.burmalda.web.dto;

import com.burmalda.domain.User;

import java.math.BigDecimal;
import java.util.List;

/** Ответы, общие для обеих ролей. */
public final class CommonViews {

    private CommonViews() {
    }

    public record UserDto(Long id, String name, String email, String role, String department, String company) {
        public static UserDto from(User u) {
            return new UserDto(u.getId(), u.getName(), u.getEmail(), u.getRole().api(),
                    u.getDepartment(), u.getCompany());
        }
    }

    public record LoginResponse(String token, UserDto user) {
    }

    public record RegisterResponse(UserDto user) {
    }

    public record Ok(boolean ok) {
        public static final Ok OK = new Ok(true);
    }

    public record OkBalance(boolean ok, BigDecimal balance) {
    }

    public record Money(BigDecimal amount, String currency) {
    }

    public record MonthValue(String m, BigDecimal v) {
    }

    /** Строка выплаты: дашборд сотрудника, кошелёк. */
    public record PayoutRow(String date, String label, BigDecimal amount, String status) {
    }

    public record TaskDto(Long id, String title, String project, String due, String prio, String status) {
    }

    public record TxRow(String id, String date, String name, String note, String dir,
                        BigDecimal amount, BigDecimal fee, String status) {
    }

    public record TxList(BigDecimal balance, List<TxRow> items) {
    }

    public record ReportDef(String id, String name, String desc, List<String> formats) {
    }

    /** content — готовый CSV, filename — как его сохранить. */
    public record ReportResult(boolean ok, String filename, String format, String content) {
    }

    public record SummaryPoint(String tone, String text) {
    }

    public record AiSummary(String updated, List<SummaryPoint> points) {
    }

    public record ChatReply(String reply) {
    }

    public record SearchHit(String type, Long id, String title, String sub) {
    }
}
