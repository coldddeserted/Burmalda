package com.burmalda.service;

import com.burmalda.domain.*;
import com.burmalda.repo.PayoutRepository;
import com.burmalda.repo.TaskRepository;
import com.burmalda.repo.TimeLogRepository;
import com.burmalda.repo.UserRepository;
import com.burmalda.util.Fmt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The heart of Burmalda: how much an employee earned in a month.
 *
 *   salary = tracked hours × hourly rate
 *            + PROJECT_BONUS for every project that ended this month where the employee had tasks
 *
 * Everything comes from real data: TimeLog (tracker), Task (tasks), Project (projects).
 */
@Service
@Transactional(readOnly = true)
public class PayrollService {

    public static final BigDecimal PROJECT_BONUS = new BigDecimal("150.00");
    /** Assumed PayPal fee for Mass Pay vs a single transfer — the difference is the saving. */
    public static final BigDecimal MASS_PAY_FEE_RATE = new BigDecimal("0.01");
    public static final BigDecimal SINGLE_PAYOUT_FEE_RATE = new BigDecimal("0.02");

    private final UserRepository users;
    private final TimeLogRepository timeLogs;
    private final TaskRepository tasks;
    private final PayoutRepository payouts;

    public PayrollService(UserRepository users, TimeLogRepository timeLogs,
                          TaskRepository tasks, PayoutRepository payouts) {
        this.users = users;
        this.timeLogs = timeLogs;
        this.tasks = tasks;
        this.payouts = payouts;
    }

    /** An employee's result for a month. */
    public record EmployeeMonth(User user, double hours, int tasksDone, int tasksTotal, int projectsEnded,
                                BigDecimal basePay, BigDecimal bonus, BigDecimal salary, boolean paid) {
    }

    public EmployeeMonth monthFor(User u, YearMonth m) {
        LocalDateTime from = m.atDay(1).atStartOfDay();
        LocalDateTime to = m.plusMonths(1).atDay(1).atStartOfDay();

        long secs = logs(u, from, to).stream().mapToLong(TimeLog::getSeconds).sum();
        double hours = Fmt.round1(secs / 3600.0);

        List<Task> my = tasks.findByAssigneeOrderByDueDateAsc(u);
        int done = 0, open = 0;
        Set<Long> endedProjects = new HashSet<>();
        for (Task t : my) {
            boolean doneInMonth = t.getCompletedAt() != null
                    && !t.getCompletedAt().isBefore(from) && t.getCompletedAt().isBefore(to);
            if (doneInMonth) done++;
            else if (t.getStatus() != TaskStatus.DONE && t.getCreatedAt().isBefore(to)) open++;

            Project p = t.getProject();
            if (p.getStatus() == ProjectStatus.ENDED && p.getEndDate() != null
                    && YearMonth.from(p.getEndDate()).equals(m)) {
                endedProjects.add(p.getId());
            }
        }

        BigDecimal base = Fmt.money(u.getHourlyRate().multiply(BigDecimal.valueOf(hours)));
        BigDecimal bonus = PROJECT_BONUS.multiply(BigDecimal.valueOf(endedProjects.size()));
        BigDecimal salary = Fmt.money(base.add(bonus));
        boolean paid = payouts.existsByUserAndPeriodAndStatus(u, m.toString(), PayoutStatus.PAID);

        return new EmployeeMonth(u, hours, done, done + open, endedProjects.size(), base, bonus, salary, paid);
    }

    /**
     * Which month the salary is paid for right now.
     * Salaries are paid AFTER the month: on November 1 we pay for October. So if anyone
     * is still unpaid for the previous month we pay for it, otherwise for the current one.
     */
    public YearMonth payPeriod(String company) {
        YearMonth prev = YearMonth.now().minusMonths(1);
        boolean prevUnpaid = companyMonth(company, prev).stream()
                .anyMatch(e -> !e.paid() && e.salary().signum() > 0);
        return prevUnpaid ? prev : YearMonth.now();
    }

    /** The same for one employee. */
    public YearMonth payPeriod(User u) {
        YearMonth prev = YearMonth.now().minusMonths(1);
        EmployeeMonth e = monthFor(u, prev);
        return !e.paid() && e.salary().signum() > 0 ? prev : YearMonth.now();
    }

    public List<User> employees(String company) {
        return users.findByCompanyAndRoleOrderByNameAsc(company, Role.EMPLOYEE);
    }

    public List<EmployeeMonth> companyMonth(String company, YearMonth m) {
        List<EmployeeMonth> res = new ArrayList<>();
        for (User u : employees(company)) res.add(monthFor(u, m));
        return res;
    }

    public static BigDecimal total(List<EmployeeMonth> list) {
        return list.stream().map(EmployeeMonth::salary).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal unpaidTotal(List<EmployeeMonth> list) {
        return list.stream().filter(e -> !e.paid()).map(EmployeeMonth::salary)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Payroll per week of the month (hours × rate), in $k. */
    public List<Double> weeklyPayrollK(String company, YearMonth m) {
        BigDecimal[] w = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        LocalDateTime from = m.atDay(1).atStartOfDay();
        LocalDateTime to = m.plusMonths(1).atDay(1).atStartOfDay();
        for (User u : employees(company)) {
            for (TimeLog l : logs(u, from, to)) {
                int i = weekOfMonth(l.getStartedAt().toLocalDate());
                BigDecimal pay = u.getHourlyRate().multiply(BigDecimal.valueOf(l.getSeconds() / 3600.0));
                w[i] = w[i].add(pay);
            }
        }
        List<Double> res = new ArrayList<>();
        for (BigDecimal v : w) res.add(Fmt.thousands(v));
        return res;
    }

    public List<TimeLog> logs(User u, LocalDateTime from, LocalDateTime to) {
        return timeLogs.findByUserAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtAsc(u, from, to);
    }

    public long secondsOn(User u, LocalDate day) {
        return logs(u, day.atStartOfDay(), day.plusDays(1).atStartOfDay()).stream()
                .mapToLong(TimeLog::getSeconds).sum();
    }

    /** 1–7 -> 0, 8–14 -> 1, 15–21 -> 2, 22+ -> 3 */
    public static int weekOfMonth(LocalDate d) {
        return Math.min((d.getDayOfMonth() - 1) / 7, 3);
    }

    /** Payday is the last day of the month. */
    public static LocalDate payday(YearMonth m) {
        return m.atEndOfMonth();
    }
}
