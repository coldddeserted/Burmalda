package com.burmalda.web.dto;

import com.burmalda.web.dto.CommonViews.Money;
import com.burmalda.web.dto.CommonViews.MonthValue;

import java.math.BigDecimal;
import java.util.List;

/** Ответы для кабинета бухгалтера. Графики фронт рисует в тысячах долларов ($k). */
public final class AccountantViews {

    private AccountantViews() {
    }

    public record Dashboard(
            Payroll payroll,
            Money pendingApprovals,
            Budget budget,
            Risk risk,
            Compliance compliance,
            CashFlow cashFlow,
            List<DashTx> transactions,
            List<DeptAmount> departments,
            Savings savings,
            Details details) {
    }

    /** weekly — зарплатный фонд по неделям месяца в $k. */
    public record Payroll(BigDecimal balance, String currency, double deltaPct,
                          BigDecimal prevMonth, String prevLabel, List<Double> weekly) {
    }

    /** splits — три суммы под карточкой "Available Budget". */
    public record Budget(BigDecimal amount, String currency, List<BigDecimal> splits) {
    }

    public record Risk(int anomalies) {
    }

    public record Compliance(int percent, List<String> items) {
    }

    /** inflow/outflow/forecast — 10 недель в $k; bars — 4 недели текущего месяца в $k. */
    public record CashFlow(List<Double> inflow, List<Double> outflow, List<Double> forecast, List<InOut> bars) {
    }

    public record InOut(double in, double out) {
    }

    public record DashTx(String id, String name, BigDecimal amount, String date, String status,
                         String badge, String color) {
    }

    public record DeptAmount(String name, BigDecimal amount) {
    }

    /** weeks — высота столбиков 0..100 */
    public record Savings(BigDecimal total, double deltaPct, List<Integer> weeks) {
    }

    public record Details(
            List<PendingRow> pending,
            List<AllocRow> alloc,
            List<AnomalyRow> anomalies,
            List<DocRow> docs,
            List<SavingRow> savings) {
    }

    public record PendingRow(Long id, String name, BigDecimal amount, String by, String due) {
    }

    public record AllocRow(String dept, BigDecimal allocated, BigDecimal spent, int heads) {
    }

    public record AnomalyRow(String txId, String title, String detail, BigDecimal amount, String level) {
    }

    public record DocRow(String name, String status, boolean ok) {
    }

    public record SavingRow(String label, BigDecimal amount) {
    }

    /* ---------- Tasks бухгалтера: кто сколько наработал ---------- */

    public record Team(String period, List<EmployeePay> employees, List<EndedProject> projects) {
    }

    /** status — Paid / Unpaid */
    public record EmployeePay(Long id, String name, String dept, int tasksDone, int tasksTotal,
                              int projectsEnded, double hours, BigDecimal salary, String status) {
    }

    public record EndedProject(String name, String lead, String date, BigDecimal budget) {
    }

    public record PayResult(boolean ok, String batchId, int paid, BigDecimal total, boolean simulated) {
    }

    /* ---------- Finance ---------- */

    public record Finance(BigDecimal paypalBalance, BigDecimal available, BigDecimal reserved, String currency,
                          String bank, List<Upcoming> upcoming, List<AllocRow> alloc, List<MonthValue> months) {
    }

    public record Upcoming(String label, String date, BigDecimal amount, String kind) {
    }
}
