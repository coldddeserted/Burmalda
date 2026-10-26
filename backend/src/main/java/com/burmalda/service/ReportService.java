package com.burmalda.service;

import com.burmalda.domain.*;
import com.burmalda.repo.PayoutRepository;
import com.burmalda.repo.PaypalTransactionRepository;
import com.burmalda.repo.TaskRepository;
import com.burmalda.util.Errors;
import com.burmalda.util.Fmt;
import com.burmalda.web.dto.AccountantViews.AllocRow;
import com.burmalda.web.dto.AccountantViews.AnomalyRow;
import com.burmalda.web.dto.AccountantViews.DocRow;
import com.burmalda.web.dto.CommonViews.ReportDef;
import com.burmalda.web.dto.CommonViews.ReportResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/** Отчёты. Генерим CSV (открывается в Excel/Google Sheets) за Month / Quarter / Year. */
@Service
@Transactional(readOnly = true)
public class ReportService {

    /** Условная ставка удержания налога для отчёта Tax withholding. */
    private static final BigDecimal TAX_RATE = new BigDecimal("0.18");

    private static final List<ReportDef> ACCOUNTANT = List.of(
            new ReportDef("payroll", "Payroll summary", "Salaries, bonuses and PayPal Mass Pay batches for the selected period.", List.of("CSV")),
            new ReportDef("tax", "Tax withholding", "Estimated withholding per employee (18%) for the selected period.", List.of("CSV")),
            new ReportDef("cash", "Cash flow", "Inflow and outflow by month from PayPal activity.", List.of("CSV")),
            new ReportDef("dept", "Department spend", "Allocated budget against actual spending for every department.", List.of("CSV")),
            new ReportDef("perf", "Tasks and projects", "Completed tasks and finished projects per employee, linked to pay.", List.of("CSV")),
            new ReportDef("compliance", "Compliance audit", "Missing documents, verification status and flagged transactions.", List.of("CSV")));

    private static final List<ReportDef> EMPLOYEE = List.of(
            new ReportDef("earn", "Earnings statement", "Salary, bonuses and refunds you received through PayPal.", List.of("CSV")),
            new ReportDef("time", "Time sheet", "Hours per day and per project from the time tracker.", List.of("CSV")),
            new ReportDef("proj", "Project summary", "Your projects, their status and your hours on them.", List.of("CSV")),
            new ReportDef("tax", "Annual tax summary", "Income by month for the current year.", List.of("CSV")));

    private final PayrollService payroll;
    private final BudgetService budgets;
    private final AnomalyService anomalies;
    private final AccountantDashboardService accountantDashboard;
    private final PayoutRepository payouts;
    private final PaypalTransactionRepository txs;
    private final TaskRepository tasks;

    public ReportService(PayrollService payroll, BudgetService budgets, AnomalyService anomalies,
                         AccountantDashboardService accountantDashboard, PayoutRepository payouts,
                         PaypalTransactionRepository txs, TaskRepository tasks) {
        this.payroll = payroll;
        this.budgets = budgets;
        this.anomalies = anomalies;
        this.accountantDashboard = accountantDashboard;
        this.payouts = payouts;
        this.txs = txs;
        this.tasks = tasks;
    }

    public List<ReportDef> list(User me) {
        return me.getRole() == Role.ACCOUNTANT ? ACCOUNTANT : EMPLOYEE;
    }

    public ReportResult generate(User me, String id, String period) {
        List<YearMonth> months = months(period);
        Csv csv = new Csv();
        boolean acc = me.getRole() == Role.ACCOUNTANT;
        String company = me.getCompany();

        switch ((acc ? "a:" : "e:") + id) {
            case "a:payroll" -> {
                csv.row("Month", "Employee", "Department", "Hours", "Base pay", "Project bonus", "Total", "Status");
                for (YearMonth m : months)
                    for (var e : payroll.companyMonth(company, m))
                        csv.row(m, e.user().getName(), e.user().getDepartment(), e.hours(), e.basePay(), e.bonus(),
                                e.salary(), e.paid() ? "Paid" : "Unpaid");
            }
            case "a:tax" -> {
                csv.row("Employee", "Gross", "Withholding 18%", "Net");
                Map<String, BigDecimal> gross = new TreeMap<>();
                for (YearMonth m : months)
                    for (var e : payroll.companyMonth(company, m))
                        gross.merge(e.user().getName(), e.salary(), BigDecimal::add);
                gross.forEach((name, g) -> {
                    BigDecimal tax = Fmt.money(g.multiply(TAX_RATE));
                    csv.row(name, Fmt.money(g), tax, Fmt.money(g.subtract(tax)));
                });
            }
            case "a:cash" -> {
                csv.row("Month", "Inflow", "Outflow", "Fees", "Net");
                List<PaypalTransaction> all = txs.findByCompanyAndOwnerIsNullOrderByDateTimeDesc(company);
                for (YearMonth m : months) {
                    BigDecimal in = BigDecimal.ZERO, out = BigDecimal.ZERO, fee = BigDecimal.ZERO;
                    for (PaypalTransaction t : all) {
                        if (t.getStatus() != TxStatus.COMPLETED || !YearMonth.from(t.getDateTime()).equals(m)) continue;
                        if (t.getDirection() == TxDirection.IN) in = in.add(t.getAmount());
                        else out = out.add(t.getAmount());
                        fee = fee.add(t.getFee());
                    }
                    csv.row(m, in, out, fee, in.subtract(out).subtract(fee));
                }
            }
            case "a:dept" -> {
                csv.row("Month", "Department", "People", "Allocated", "Spent", "Left");
                for (YearMonth m : months)
                    for (AllocRow a : budgets.allocation(company, m))
                        csv.row(m, a.dept(), a.heads(), a.allocated(), a.spent(), a.allocated().subtract(a.spent()));
            }
            case "a:perf" -> {
                csv.row("Month", "Employee", "Tasks done", "Tasks total", "Projects ended", "Hours", "Salary");
                for (YearMonth m : months)
                    for (var e : payroll.companyMonth(company, m))
                        csv.row(m, e.user().getName(), e.tasksDone(), e.tasksTotal(), e.projectsEnded(), e.hours(), e.salary());
            }
            case "a:compliance" -> {
                csv.row("Type", "Item", "Status", "OK");
                for (DocRow d : accountantDashboard.docs(company)) csv.row("Document", d.name(), d.status(), d.ok());
                for (AnomalyRow a : anomalies.detect(company))
                    csv.row("Flagged transaction", a.title() + " (" + a.txId() + ")", a.detail(), false);
            }
            case "e:earn" -> {
                csv.row("Date", "Label", "Amount", "Status", "Batch");
                for (Payout p : payouts.findByUserOrderByDateDesc(me))
                    if (months.contains(YearMonth.from(p.getDate())))
                        csv.row(Fmt.date(p.getDate()), p.getLabel(), p.getAmount(), p.getStatus().api(), p.getBatchId());
            }
            case "e:time" -> {
                csv.row("Date", "Started", "Project", "Hours");
                LocalDate from = months.get(0).atDay(1);
                LocalDate to = months.get(months.size() - 1).plusMonths(1).atDay(1);
                for (TimeLog l : payroll.logs(me, from.atStartOfDay(), to.atStartOfDay()))
                    csv.row(Fmt.date(l.getStartedAt().toLocalDate()), Fmt.hm(l.getStartedAt()), l.getLabel(),
                            Fmt.round1(l.getSeconds() / 3600.0));
            }
            case "e:proj" -> {
                csv.row("Project", "Status", "Progress %", "My tasks", "My tasks done");
                Map<Project, List<Task>> byProject = new LinkedHashMap<>();
                for (Task t : tasks.findByAssigneeOrderByDueDateAsc(me))
                    byProject.computeIfAbsent(t.getProject(), k -> new ArrayList<>()).add(t);
                byProject.forEach((p, list) -> csv.row(p.getName(), p.getStatus(), p.getProgress(), list.size(),
                        list.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count()));
            }
            case "e:tax" -> {
                csv.row("Month", "Income");
                YearMonth m = YearMonth.of(LocalDate.now().getYear(), 1);
                List<Payout> mine = payouts.findByUserOrderByDateDesc(me);
                while (!m.isAfter(YearMonth.now())) {
                    YearMonth cur = m;
                    BigDecimal v = mine.stream().filter(p -> p.getStatus() == PayoutStatus.PAID
                                    && YearMonth.from(p.getDate()).equals(cur))
                            .map(Payout::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                    csv.row(m, v);
                    m = m.plusMonths(1);
                }
            }
            default -> throw Errors.notFound("Отчёт " + id + " не найден");
        }
        String p = period == null ? "month" : period.toLowerCase();
        return new ReportResult(true, id + "-" + p + "-" + LocalDate.now() + ".csv", "CSV", csv.toString());
    }

    /** Month -> текущий месяц, Quarter -> 3 последних, Year -> с января. */
    private static List<YearMonth> months(String period) {
        YearMonth now = YearMonth.now();
        int n = switch (period == null ? "month" : period.toLowerCase()) {
            case "quarter" -> 3;
            case "year" -> now.getMonthValue();
            default -> 1;
        };
        List<YearMonth> res = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) res.add(now.minusMonths(i));
        return res;
    }

    /** Мини-построитель CSV с экранированием кавычек. */
    private static final class Csv {
        private final StringBuilder sb = new StringBuilder();

        void row(Object... cells) {
            for (int i = 0; i < cells.length; i++) {
                if (i > 0) sb.append(',');
                String v = cells[i] == null ? "" : cells[i].toString();
                if (v.contains(",") || v.contains("\"") || v.contains("\n")) v = "\"" + v.replace("\"", "\"\"") + "\"";
                sb.append(v);
            }
            sb.append('\n');
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }
}
