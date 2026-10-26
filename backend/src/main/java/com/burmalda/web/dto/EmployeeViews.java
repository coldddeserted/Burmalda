package com.burmalda.web.dto;

import com.burmalda.web.dto.CommonViews.Money;
import com.burmalda.web.dto.CommonViews.MonthValue;
import com.burmalda.web.dto.CommonViews.PayoutRow;

import java.math.BigDecimal;
import java.util.List;

/** Ответы для кабинета сотрудника. */
public final class EmployeeViews {

    private EmployeeViews() {
    }

    public record Dashboard(
            Money totalEarned,
            CountDelta endedProjects,
            CountDelta runningProjects,
            CountLabel discussProjects,
            List<DayBar> analytics,
            List<TeamMember> team,
            Progress progress,
            Timer timer,
            Details details) {
    }

    public record CountDelta(int count, int delta) {
    }

    public record CountLabel(int count, String label) {
    }

    /** value — 0..100 (высота столбика), type — light/mid/dark/hatched. */
    public record DayBar(String day, int value, String type, String tip) {
    }

    /** status — progress / pending / completed */
    public record TeamMember(Long id, String name, String task, String status) {
    }

    /** Проценты задач, в сумме 100. */
    public record Progress(int completed, int inProgress, int pending) {
    }

    public record Timer(long seconds) {
    }

    public record Details(
            List<PayoutRow> payouts,
            List<EndedRow> ended,
            List<RunningRow> running,
            List<DiscussRow> discuss,
            List<SessionRow> sessions) {
    }

    public record EndedRow(String name, String date, BigDecimal pay) {
    }

    public record RunningRow(String name, int progress, String due) {
    }

    public record DiscussRow(String name, String note) {
    }

    public record SessionRow(String project, String from, String dur) {
    }

    public record Wallet(
            BigDecimal balance,
            String currency,
            BigDecimal pending,
            String nextPayday,
            PaypalAccount paypal,
            List<MonthValue> months,
            List<PayoutRow> history) {
    }

    public record PaypalAccount(String email, boolean verified) {
    }
}
