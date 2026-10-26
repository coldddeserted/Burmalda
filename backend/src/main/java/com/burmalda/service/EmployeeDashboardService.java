package com.burmalda.service;

import com.burmalda.domain.*;
import com.burmalda.repo.PayoutRepository;
import com.burmalda.repo.TaskRepository;
import com.burmalda.repo.UserRepository;
import com.burmalda.util.Fmt;
import com.burmalda.web.dto.CommonViews.Money;
import com.burmalda.web.dto.CommonViews.PayoutRow;
import com.burmalda.web.dto.EmployeeViews.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** GET /api/dashboard/employee — всё для главной сотрудника. */
@Service
@Transactional(readOnly = true)
public class EmployeeDashboardService {

    private static final String[] DAY_LETTERS = {"S", "M", "T", "W", "T", "F", "S"};
    /** Сколько часов за день считаем за 100% высоты столбика. */
    private static final double FULL_DAY_HOURS = 10.0;

    private final TaskRepository tasks;
    private final UserRepository users;
    private final PayoutRepository payouts;
    private final PayrollService payroll;

    public EmployeeDashboardService(TaskRepository tasks, UserRepository users,
                                    PayoutRepository payouts, PayrollService payroll) {
        this.tasks = tasks;
        this.users = users;
        this.payouts = payouts;
        this.payroll = payroll;
    }

    public Dashboard build(User me) {
        LocalDate today = LocalDate.now();
        YearMonth month = YearMonth.now();
        List<Task> myTasks = tasks.findByAssigneeOrderByDueDateAsc(me);

        // проекты, в которых у меня есть задачи
        Map<Long, Project> projects = new LinkedHashMap<>();
        for (Task t : myTasks) projects.putIfAbsent(t.getProject().getId(), t.getProject());
        List<Project> ended = projects.values().stream().filter(p -> p.getStatus() == ProjectStatus.ENDED)
                .sorted(Comparator.comparing(Project::getEndDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<Project> running = projects.values().stream().filter(p -> p.getStatus() == ProjectStatus.RUNNING).toList();
        List<Project> discuss = projects.values().stream().filter(p -> p.getStatus() == ProjectStatus.DISCUSS).toList();

        int endedThisMonth = (int) ended.stream()
                .filter(p -> p.getEndDate() != null && YearMonth.from(p.getEndDate()).equals(month)).count();
        int startedThisMonth = (int) running.stream()
                .filter(p -> p.getStartDate() != null && YearMonth.from(p.getStartDate()).equals(month)).count();

        // все логи за полгода — для "сколько заработал на проекте"
        List<TimeLog> logs = payroll.logs(me, month.minusMonths(6).atDay(1).atStartOfDay(),
                today.plusDays(1).atStartOfDay());

        return new Dashboard(
                new Money(Fmt.money(me.getWalletBalance()), "USD"),
                new CountDelta(ended.size(), endedThisMonth),
                new CountDelta(running.size(), startedThisMonth),
                new CountLabel(discuss.size(), "On Discuss"),
                weekAnalytics(me, today),
                team(me),
                progress(myTasks),
                new Timer(payroll.secondsOn(me, today)),
                new Details(
                        payoutRows(me, month),
                        ended.stream().map(p -> new EndedRow(p.getName(), Fmt.date(p.getEndDate()),
                                earnedOn(me, logs, p.getName()))).toList(),
                        running.stream().map(p -> new RunningRow(p.getName(), p.getProgress(),
                                Fmt.date(p.getDueDate()))).toList(),
                        discuss.stream().map(p -> new DiscussRow(p.getName(),
                                p.getNote() == null ? "" : p.getNote())).toList(),
                        sessions(me, today)));
    }

    /** Столбики по дням текущей недели (Вс..Сб). Будущие дни — штрихованные, показывают прошлую неделю. */
    private List<DayBar> weekAnalytics(User me, LocalDate today) {
        LocalDate sunday = today.minusDays(today.getDayOfWeek().getValue() % 7);
        int[] values = new int[7];
        int maxIdx = -1;
        for (int i = 0; i < 7; i++) {
            LocalDate d = sunday.plusDays(i);
            LocalDate source = d.isAfter(today) ? d.minusWeeks(1) : d;
            double hours = payroll.secondsOn(me, source) / 3600.0;
            values[i] = (int) Math.min(100, Math.round(hours / FULL_DAY_HOURS * 100));
            if (!d.isAfter(today) && (maxIdx < 0 || values[i] > values[maxIdx])) maxIdx = i;
        }
        List<DayBar> res = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = sunday.plusDays(i);
            String type;
            String tip = null;
            if (d.isAfter(today)) type = "hatched";
            else if (i == maxIdx) type = "dark";
            else if (d.equals(today)) type = "mid";
            else type = "light";
            if (d.equals(today)) tip = values[i] + "%";
            res.add(new DayBar(DAY_LETTERS[i], values[i], type, tip));
        }
        return res;
    }

    /** Коллеги из моего отдела и над чем они сейчас работают. */
    private List<TeamMember> team(User me) {
        List<User> mates = users.findByCompanyAndDepartmentAndRole(me.getCompany(), me.getDepartment(), Role.EMPLOYEE);
        List<TeamMember> res = new ArrayList<>();
        for (User u : mates) {
            List<Task> t = tasks.findByAssigneeOrderByDueDateAsc(u);
            Optional<Task> current = t.stream().filter(x -> x.getStatus() == TaskStatus.PROGRESS).findFirst()
                    .or(() -> t.stream().filter(x -> x.getStatus() == TaskStatus.TODO).findFirst())
                    .or(() -> t.stream().filter(x -> x.getStatus() == TaskStatus.DONE)
                            .max(Comparator.comparing(Task::getCompletedAt, Comparator.nullsFirst(Comparator.naturalOrder()))));
            String status = current.map(x -> switch (x.getStatus()) {
                case PROGRESS -> "progress";
                case TODO -> "pending";
                case DONE -> "completed";
            }).orElse("pending");
            res.add(new TeamMember(u.getId(), u.getName(), current.map(Task::getTitle).orElse("—"), status));
            if (res.size() == 6) break;
        }
        return res;
    }

    /** Проценты задач: закрыто / в работе / ждут. В сумме ровно 100. */
    private Progress progress(List<Task> my) {
        if (my.isEmpty()) return new Progress(0, 0, 100);
        long done = my.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        long prog = my.stream().filter(t -> t.getStatus() == TaskStatus.PROGRESS).count();
        int c = (int) Math.round(done * 100.0 / my.size());
        int p = (int) Math.round(prog * 100.0 / my.size());
        return new Progress(c, p, Math.max(0, 100 - c - p));
    }

    /** Ближайшая выплата (если зарплата за месяц ещё не пришла) + последние реальные. */
    private List<PayoutRow> payoutRows(User me, YearMonth month) {
        List<PayoutRow> rows = new ArrayList<>();
        PayrollService.EmployeeMonth cur = payroll.monthFor(me, month);
        if (!cur.paid() && cur.salary().signum() > 0) {
            rows.add(new PayoutRow(Fmt.date(PayrollService.payday(month)),
                    Fmt.monthLong(month) + " salary", cur.salary(), "Scheduled"));
        }
        for (Payout p : payouts.findByUserOrderByDateDesc(me)) {
            if (rows.size() == 4) break;
            rows.add(new PayoutRow(Fmt.date(p.getDate()), p.getLabel(), p.getAmount(), p.getStatus().api()));
        }
        return rows;
    }

    /** Сколько я заработал на проекте: мои часы на нём × ставка. */
    private BigDecimal earnedOn(User me, List<TimeLog> logs, String projectName) {
        long secs = logs.stream().filter(l -> projectName.equalsIgnoreCase(l.getLabel()))
                .mapToLong(TimeLog::getSeconds).sum();
        return Fmt.money(me.getHourlyRate().multiply(BigDecimal.valueOf(secs / 3600.0)));
    }

    private List<SessionRow> sessions(User me, LocalDate today) {
        LocalDateTime from = today.atStartOfDay();
        return payroll.logs(me, from, from.plusDays(1)).stream()
                .map(l -> new SessionRow(l.getLabel(), Fmt.hm(l.getStartedAt()), Fmt.dur(l.getSeconds())))
                .toList();
    }
}
