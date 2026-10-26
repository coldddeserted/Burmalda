package com.burmalda.config;

import com.burmalda.domain.*;
import com.burmalda.repo.*;
import com.burmalda.service.PayrollService;
import com.burmalda.util.Fmt;
import com.burmalda.util.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/**
 * Демо-данные при первом запуске (если в БД нет пользователей).
 * Все даты считаются от "сегодня", поэтому дашборды всегда выглядят живыми.
 * Отключить: app.seed.enabled=false. Пересоздать: удалить папку ./data и перезапустить.
 *
 * Логины (пароль у всех demo1234):
 *   accountant@burmalda.com — бухгалтер
 *   engineer@burmalda.com   — сотрудник (Edgar Wilson)
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String COMPANY = "Burmalda";
    private static final String PASSWORD = "demo1234";
    private static final int HISTORY_MONTHS = 5;

    private final boolean enabled;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final TimeLogRepository timeLogs;
    private final PayoutRepository payouts;
    private final PaypalTransactionRepository txs;
    private final ScheduledPaymentRepository scheduled;
    private final DepartmentBudgetRepository budgets;
    private final CompanyAccountRepository accounts;
    private final ComplianceItemRepository compliance;
    private final ChatMessageRepository chats;
    private final PayrollService payroll;
    private final PasswordEncoder encoder;

    private final Random rnd = new Random(42);
    private final LocalDateTime now = LocalDateTime.now();
    private final LocalDate today = now.toLocalDate();
    private final YearMonth month = YearMonth.now();

    public DataSeeder(@Value("${app.seed.enabled:true}") boolean enabled, UserRepository users,
                      ProjectRepository projects, TaskRepository tasks, TimeLogRepository timeLogs,
                      PayoutRepository payouts, PaypalTransactionRepository txs, ScheduledPaymentRepository scheduled,
                      DepartmentBudgetRepository budgets, CompanyAccountRepository accounts,
                      ComplianceItemRepository compliance, ChatMessageRepository chats,
                      PayrollService payroll, PasswordEncoder encoder) {
        this.enabled = enabled;
        this.users = users;
        this.projects = projects;
        this.tasks = tasks;
        this.timeLogs = timeLogs;
        this.payouts = payouts;
        this.txs = txs;
        this.scheduled = scheduled;
        this.budgets = budgets;
        this.accounts = accounts;
        this.compliance = compliance;
        this.chats = chats;
        this.payroll = payroll;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled || users.count() > 0) return;
        log.info("Seeding demo data for company '{}'...", COMPANY);

        String hash = encoder.encode(PASSWORD); // bcrypt медленный — считаем один раз

        /* ---------- люди ---------- */
        User anna = user("Anna Kowalska", "accountant@burmalda.com", Role.ACCOUNTANT, "Finance", "0", hash);
        User edgar = user("Edgar Wilson", "engineer@burmalda.com", Role.EMPLOYEE, "Engineering", "20", hash);
        User leon = user("Leon Isa", "leon@burmalda.com", Role.EMPLOYEE, "Engineering", "25", hash);
        User alex = user("Alexandra Kim", "alexandra@burmalda.com", Role.EMPLOYEE, "Engineering", "23", hash);
        User jack = user("Jack Lui", "jack@burmalda.com", Role.EMPLOYEE, "Engineering", "19", hash);
        User maria = user("Maria Santos", "maria@burmalda.com", Role.EMPLOYEE, "Design", "21", hash);
        User tomasz = user("Tomasz Nowak", "tomasz@burmalda.com", Role.EMPLOYEE, "Sales", "18", hash);
        User sofia = user("Sofia Rossi", "sofia@burmalda.com", Role.EMPLOYEE, "Marketing", "20", hash);
        User daniel = user("Daniel Park", "daniel@burmalda.com", Role.EMPLOYEE, "Operations", "17", hash);
        tomasz.setPaypalVerified(false); // для Compliance: один профиль не подтверждён
        List<User> employees = List.of(edgar, leon, alex, jack, maria, tomasz, sofia, daniel);

        /* ---------- проекты ---------- */
        LocalDate m1 = month.atDay(1);
        LocalDate prevEnd = month.minusMonths(1).atEndOfMonth();
        Project billing = ended("Billing API v2", alex, clampToMonth(today.minusDays(4)), 18000);
        Project portal = ended("Customer portal beta", leon, clampToMonth(today.minusDays(7)), 24500);
        Project brand = ended("Brand refresh", maria, clampToMonth(today.minusDays(11)), 9200);
        Project partner = ended("Partner onboarding flow", sofia, clampToMonth(today.minusDays(16)), 7400);
        Project admin = ended("Admin dashboard", edgar, clampToMonth(today.minusDays(19)), 12800);
        Project landing = ended("Landing page redesign", edgar, prevEnd.minusDays(3), 8600);
        Project emails = ended("Email templates", sofia, prevEnd.minusDays(9), 3100);
        Project docs = ended("Docs portal", jack, prevEnd.minusDays(14), 5600);
        Project repo = ended("Repository migration", jack, prevEnd.minusDays(20), 5100);

        Project layout = running("Responsive Layout for Homepage", leon, m1.minusMonths(1), today.plusDays(9));
        Project search = running("Search and Filter Functionality", edgar, m1, today.plusDays(12));
        Project auth = running("User Authentication System", alex, m1.minusMonths(1), today.plusDays(16));
        Project ci = running("CI/CD pipeline", jack, m1, today.plusDays(21));
        Project invoice = running("Invoice PDF generator", edgar, m1.minusMonths(1), today.plusDays(6));
        Project notify = running("Notification service", daniel, m1, today.plusDays(25));
        Project crm = running("Sales CRM sync", tomasz, m1.minusMonths(1), today.plusDays(14));
        Project campaign = running("Campaign analytics", sofia, m1, today.plusDays(18));
        Project design = running("Design system v2", maria, m1.minusMonths(1), today.plusDays(30));

        Project partnerApi = discuss("Partner API integration", "Waiting for scope from the client");
        Project dwh = discuss("Data warehouse migration", "Budget approval pending");

        /* ---------- задачи ---------- */
        // Edgar (демо-сотрудник) — чтобы его доска была разнообразной
        task("Develop Search and Filter Functionality", search, edgar, 3, Priority.HIGH, TaskStatus.TODO);
        task("Filter chips UI", search, edgar, 6, Priority.MID, TaskStatus.PROGRESS);
        task("Search API pagination", search, edgar, -2, Priority.MID, TaskStatus.DONE);
        task("Fix invoice PDF page breaks", invoice, edgar, -1, Priority.HIGH, TaskStatus.PROGRESS);
        task("Invoice totals rounding", invoice, edgar, -6, Priority.LOW, TaskStatus.DONE);
        task("Responsive layout for pricing page", layout, edgar, 6, Priority.MID, TaskStatus.PROGRESS);
        task("Write unit tests for auth module", auth, edgar, 4, Priority.MID, TaskStatus.TODO);
        task("Set up CI cache for builds", ci, edgar, 8, Priority.LOW, TaskStatus.TODO);
        task("Review pull request #218", ci, edgar, 1, Priority.LOW, TaskStatus.TODO);
        task("Estimate Partner API scope", partnerApi, edgar, 10, Priority.LOW, TaskStatus.TODO);
        task("Draft warehouse migration plan", dwh, edgar, 14, Priority.LOW, TaskStatus.TODO);
        doneTask("Admin dashboard: charts", admin, edgar);
        doneTask("Admin dashboard: roles", admin, edgar);
        doneTask("Billing API: webhooks", billing, edgar);
        doneTask("Landing hero section", landing, edgar);

        // остальная команда
        task("Header and nav breakpoints", layout, leon, 4, Priority.HIGH, TaskStatus.PROGRESS);
        task("Image lazy loading", layout, leon, -3, Priority.MID, TaskStatus.DONE);
        doneTask("Portal: invoices page", portal, leon);
        doneTask("Portal: SSO login", portal, leon);
        doneTask("Portal: QA pass", portal, leon);
        task("Integrate User Authentication System", auth, alex, 5, Priority.HIGH, TaskStatus.PROGRESS);
        task("Password reset flow", auth, alex, -4, Priority.MID, TaskStatus.DONE);
        task("Rate limiting", auth, alex, -1, Priority.MID, TaskStatus.DONE);
        doneTask("Billing API: invoices endpoint", billing, alex);
        doneTask("Billing API: refunds", billing, alex);
        doneTask("Billing API: docs", billing, alex);
        task("GitHub Actions matrix", ci, jack, -5, Priority.MID, TaskStatus.DONE);
        doneTask("Docs portal: search page", docs, jack);
        doneTask("Repository migration: history rewrite", repo, jack);
        task("Brand guidelines PDF", design, maria, 7, Priority.MID, TaskStatus.PROGRESS);
        task("Button and input tokens", design, maria, -2, Priority.HIGH, TaskStatus.DONE);
        doneTask("Brand refresh: logo set", brand, maria);
        doneTask("Brand refresh: website colors", brand, maria);
        task("Map CRM fields", crm, tomasz, 2, Priority.HIGH, TaskStatus.PROGRESS);
        task("Import historic deals", crm, tomasz, 9, Priority.MID, TaskStatus.TODO);
        task("Deduplicate contacts", crm, tomasz, 12, Priority.LOW, TaskStatus.TODO);
        task("Sales dashboard filters", crm, tomasz, -3, Priority.LOW, TaskStatus.DONE);
        task("Campaign funnel report", campaign, sofia, 5, Priority.MID, TaskStatus.PROGRESS);
        task("UTM cleanup", campaign, sofia, -2, Priority.LOW, TaskStatus.DONE);
        doneTask("Partner onboarding: emails", partner, sofia);
        doneTask("Partner onboarding: checklist", partner, sofia);
        doneTask("Email templates: receipts", emails, sofia);
        task("Push provider setup", notify, daniel, 6, Priority.MID, TaskStatus.PROGRESS);
        task("Notification preferences", notify, daniel, 11, Priority.LOW, TaskStatus.TODO);
        task("Office equipment inventory", notify, daniel, -2, Priority.LOW, TaskStatus.DONE);

        // прогресс running-проектов из задач
        for (Project p : projects.findAll()) {
            List<Task> list = tasks.findByProject(p);
            if (p.getStatus() == ProjectStatus.RUNNING && !list.isEmpty()) {
                long done = list.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
                p.setProgress((int) Math.max(10, Math.round(done * 100.0 / list.size())));
            }
        }

        /* ---------- трекер времени за ~полгода ---------- */
        Map<User, List<Project>> workOn = new LinkedHashMap<>();
        for (User u : employees) {
            Set<Project> ps = new LinkedHashSet<>();
            for (Task t : tasks.findByAssigneeOrderByDueDateAsc(u)) {
                if (t.getProject().getStatus() != ProjectStatus.DISCUSS) ps.add(t.getProject());
            }
            workOn.put(u, new ArrayList<>(ps));
        }
        List<TimeLog> logs = new ArrayList<>();
        LocalDate start = month.minusMonths(HISTORY_MONTHS).atDay(1);
        for (User u : employees) {
            for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
                DayOfWeek w = d.getDayOfWeek();
                if (w == DayOfWeek.SATURDAY || w == DayOfWeek.SUNDAY) continue;
                if (rnd.nextInt(100) < 4) continue; // иногда выходной / больничный
                logs.addAll(day(u, d, workOn.get(u)));
            }
        }
        timeLogs.saveAll(logs);

        /* ---------- кошелёк компании, бюджеты, документы ---------- */
        CompanyAccount acc = new CompanyAccount(COMPANY);
        acc.setBalance(new BigDecimal("84250.00"));
        acc.setBankLabel("Bank account ending 4417");
        acc.setPaypalVerified(true);
        accounts.save(acc);

        budgets.save(new DepartmentBudget(COMPANY, "Engineering", new BigDecimal("52000")));
        budgets.save(new DepartmentBudget(COMPANY, "Marketing", new BigDecimal("24000")));
        budgets.save(new DepartmentBudget(COMPANY, "Sales", new BigDecimal("15000")));
        budgets.save(new DepartmentBudget(COMPANY, "Design", new BigDecimal("12000")));
        budgets.save(new DepartmentBudget(COMPANY, "Operations", new BigDecimal("9000")));

        int prevQuarter = (month.getMonthValue() - 1) / 3 == 0 ? 4 : (month.getMonthValue() - 1) / 3;
        compliance.save(new ComplianceItem(COMPANY, "Tax report Q" + prevQuarter, "Submitted", true));
        compliance.save(new ComplianceItem(COMPANY, "PayPal business verification", "Verified", true));
        compliance.save(new ComplianceItem(COMPANY, "Contractor tax forms", "3 missing", false));

        /* ---------- зарплаты прошлых месяцев (посчитаны тем же PayrollService) ---------- */
        for (int i = HISTORY_MONTHS; i >= 1; i--) {
            YearMonth ym = month.minusMonths(i);
            LocalDateTime payAt = ym.plusMonths(1).atDay(1).atTime(9, 0);
            String batch = "SIM-" + ym.toString().replace("-", "") + "MP";
            BigDecimal total = BigDecimal.ZERO;
            for (User u : employees) {
                BigDecimal salary = payroll.monthFor(u, ym).salary();
                payout(u, salary, Fmt.monthLong(ym) + " salary", payAt.toLocalDate(), ym, batch);
                total = total.add(salary);
            }
            companyTx(payAt, "Mass Pay: " + employees.size() + " recipients", "Payroll " + Fmt.monthYear(ym),
                    TxDirection.OUT, total, total.multiply(PayrollService.MASS_PAY_FEE_RATE), TxCategory.PAYROLL, null,
                    TxStatus.COMPLETED);
        }
        // Jack уже получил за текущий месяц (как в макете: "Jack Lui is already paid")
        BigDecimal jackNow = payroll.monthFor(jack, month).salary();
        payout(jack, jackNow, Fmt.monthLong(month) + " salary", today, month, "SIM-ADVANCE");
        companyTx(now.minusHours(2), "Mass Pay: 1 recipients", "Payroll " + Fmt.monthYear(month) + " (advance)",
                TxDirection.OUT, jackNow, jackNow.multiply(PayrollService.MASS_PAY_FEE_RATE), TxCategory.PAYROLL,
                null, TxStatus.COMPLETED);
        // бонус Эдгару за прошлый квартал
        payout(edgar, new BigDecimal("800.00"), "Bonus: Q" + prevQuarter + " milestone",
                month.minusMonths(1).atDay(15), null, "SIM-BONUS");

        // вывод на банк: каждый месяц ~60% зарплаты уходит на банк
        for (User u : employees) {
            for (int i = HISTORY_MONTHS; i >= 1; i--) {
                YearMonth ym = month.minusMonths(i).plusMonths(1);
                LocalDateTime at = ym.atDay(Math.min(20, ym.lengthOfMonth())).atTime(12, 40);
                if (at.isAfter(now)) continue;
                // ~60% зарплаты, пришедшей 1-го числа, округлено вниз до сотни
                BigDecimal salary = payroll.monthFor(u, ym.minusMonths(1)).salary();
                BigDecimal amount = salary.multiply(new BigDecimal("0.6"))
                        .divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.DOWN).multiply(BigDecimal.valueOf(100));
                if (amount.signum() <= 0) continue;
                u.setWalletBalance(u.getWalletBalance().subtract(amount));
                personalTx(u, at, "Withdrawal to bank", "Bank account linked to PayPal", TxDirection.OUT, amount,
                        TxCategory.WITHDRAWAL, TxStatus.COMPLETED);
            }
        }
        personalTx(edgar, month.minusMonths(2).atDay(12).atTime(15, 2), "Burmalda Inc.", "Expense refund: conference",
                TxDirection.IN, new BigDecimal("240.00"), TxCategory.REFUND, TxStatus.COMPLETED);
        edgar.setWalletBalance(edgar.getWalletBalance().add(new BigDecimal("240.00")));
        personalTx(edgar, month.minusMonths(3).atDay(5).atTime(11, 30), "Withdrawal to bank", "Rejected by bank",
                TxDirection.OUT, new BigDecimal("1000.00"), TxCategory.WITHDRAWAL, TxStatus.FAILED);

        /* ---------- расходы и доходы компании ---------- */
        for (int i = HISTORY_MONTHS; i >= 0; i--) {
            YearMonth ym = month.minusMonths(i);
            companyTx(at(ym, 1, 9, 0), "Funding from bank", "Bank account ending 4417", TxDirection.IN,
                    new BigDecimal("40000"), BigDecimal.ZERO, TxCategory.FUNDING, null, TxStatus.COMPLETED);
            companyTx(at(ym, 1, 10, 15), "Office rent", "Operations, monthly", TxDirection.OUT,
                    new BigDecimal("5200"), BigDecimal.ZERO, TxCategory.EXPENSE, "Operations", TxStatus.COMPLETED);
            companyTx(at(ym, 3, 18, 30), "Figma Team Plan", "Vendor", TxDirection.OUT,
                    new BigDecimal("850"), BigDecimal.ZERO, TxCategory.VENDOR, "Design", TxStatus.COMPLETED);
            companyTx(at(ym, 4, 15, 30), "Slack Technologies", "Vendor, monthly plan", TxDirection.OUT,
                    new BigDecimal("1200"), BigDecimal.ZERO, TxCategory.VENDOR, "Engineering", TxStatus.COMPLETED);
            companyTx(at(ym, 4, 17, 0), "Server Hosting", "Expense, monthly", TxDirection.OUT,
                    new BigDecimal("4350"), BigDecimal.ZERO, TxCategory.EXPENSE, "Engineering", TxStatus.COMPLETED);
            companyTx(at(ym, 6, 11, 0), "HubSpot", "Vendor, CRM", TxDirection.OUT,
                    new BigDecimal("900"), BigDecimal.ZERO, TxCategory.VENDOR, "Sales", TxStatus.COMPLETED);
            companyTx(at(ym, 10, 13, 0), "Google Ads", "Marketing campaigns", TxDirection.OUT,
                    new BigDecimal("3200"), BigDecimal.ZERO, TxCategory.VENDOR, "Marketing", TxStatus.COMPLETED);
            companyTx(at(ym, 14, 10, 5), "Contoso GmbH", "Client payment", TxDirection.IN,
                    new BigDecimal("12400"), new BigDecimal("434"), TxCategory.CLIENT, null, TxStatus.COMPLETED);
            companyTx(at(ym, 28, 14, 12), "Northwind Ltd", "Client payment", TxDirection.IN,
                    new BigDecimal("18500"), new BigDecimal("647.50"), TxCategory.CLIENT, null, TxStatus.COMPLETED);
            if (ym.getMonthValue() % 3 == 1) {
                companyTx(at(ym, 2, 10, 30), "Quarterly Tax Withholding", "Tax authority", TxDirection.OUT,
                        new BigDecimal("6100"), BigDecimal.ZERO, TxCategory.TAX, null, TxStatus.COMPLETED);
            }
        }
        // подрядчик в Лиссабоне: обычно ~1000, а последний перевод в 2.4 раза больше -> AI Risk Alert
        companyTx(month.minusMonths(3).atDay(3).atTime(8, 30), "Lisbon contractor", "Cross-border transfer, EUR",
                TxDirection.OUT, new BigDecimal("1000"), new BigDecimal("15"), TxCategory.TRANSFER, "Marketing", TxStatus.COMPLETED);
        companyTx(month.minusMonths(2).atDay(3).atTime(8, 30), "Lisbon contractor", "Cross-border transfer, EUR",
                TxDirection.OUT, new BigDecimal("1100"), new BigDecimal("16.50"), TxCategory.TRANSFER, "Marketing", TxStatus.COMPLETED);
        companyTx(now.minusDays(3).withHour(8).withMinute(30), "Lisbon contractor", "Cross-border transfer, EUR",
                TxDirection.OUT, new BigDecimal("2500"), new BigDecimal("37.50"), TxCategory.TRANSFER, "Marketing", TxStatus.COMPLETED);
        // дубль Slack на следующий день после последнего списания -> AI Risk Alert
        LocalDate lastSlack = today.getDayOfMonth() >= 5 ? month.atDay(4) : month.minusMonths(1).atDay(4);
        companyTx(lastSlack.plusDays(1).atTime(9, 10), "Slack Technologies", "Vendor, monthly plan", TxDirection.OUT,
                new BigDecimal("1200"), BigDecimal.ZERO, TxCategory.VENDOR, "Engineering", TxStatus.COMPLETED);
        companyTx(month.minusMonths(1).atDay(22).atTime(13, 20), "Notion", "Vendor, card declined", TxDirection.OUT,
                new BigDecimal("360"), BigDecimal.ZERO, TxCategory.VENDOR, "Operations", TxStatus.FAILED);

        /* ---------- ждут одобрения / запланированы ---------- */
        sched("Mass Pay: Marketing bonuses", "6800", month.atEndOfMonth(), "Payroll", "Sofia Rossi", false);
        sched("Expense: Server Hosting (next month)", "4350", today.plusDays(2), "Expense", "Leon Isa", false);
        sched("Cross-Border Transfer: Lisbon contractor", "2500", today.plusDays(1), "Transfer", "Maria Santos", false);
        sched("Vendor: Notion + Linear", "1020", today.plusDays(4), "Vendor", "Daniel Park", false);
        YearMonth nextQuarter = month.plusMonths(3 - (month.getMonthValue() - 1) % 3); // 1-е число след. квартала
        sched("Quarterly tax withholding", "6100", nextQuarter.atDay(15), "Tax", "Anna Kowalska", true);
        sched("Slack Technologies", "1200", month.plusMonths(1).atDay(4), "Vendor", "Leon Isa", true);

        /* ---------- немного истории AI-чата ---------- */
        chat(anna, "Payroll run for " + Fmt.monthLong(month.minusMonths(1)));
        chat(anna, "Anomalies in vendor payments");
        chat(anna, "Q" + prevQuarter + " tax withholding check");
        chat(edgar, "Next payday");
        chat(edgar, "Withdrawal to bank");
        chat(edgar, "Hours report for " + Fmt.monthLong(month.minusMonths(1)));

        log.info("Demo data ready: {} users, {} projects, {} tasks, {} time logs, {} transactions",
                users.count(), projects.count(), tasks.count(), timeLogs.count(), txs.count());
    }

    /* ======================= helpers ======================= */

    private User user(String name, String email, Role role, String dept, String rate, String hash) {
        User u = new User();
        u.setName(name);
        u.setEmail(email);
        u.setPasswordHash(hash);
        u.setRole(role);
        u.setDepartment(dept);
        u.setCompany(COMPANY);
        u.setHourlyRate(new BigDecimal(rate));
        u.setPaypalEmail(email);
        u.setPaypalVerified(true);
        u.setCreatedAt(now.minusMonths(HISTORY_MONTHS + 1));
        return users.save(u);
    }

    private Project base(String name, User lead, ProjectStatus status) {
        Project p = new Project();
        p.setName(name);
        p.setCompany(COMPANY);
        p.setLead(lead);
        p.setStatus(status);
        return p;
    }

    private Project ended(String name, User lead, LocalDate end, int budget) {
        Project p = base(name, lead, ProjectStatus.ENDED);
        p.setStartDate(end.minusMonths(2));
        p.setDueDate(end);
        p.setEndDate(end);
        p.setProgress(100);
        p.setBudget(BigDecimal.valueOf(budget));
        return projects.save(p);
    }

    private Project running(String name, User lead, LocalDate startDate, LocalDate due) {
        Project p = base(name, lead, ProjectStatus.RUNNING);
        p.setStartDate(startDate);
        p.setDueDate(due);
        p.setBudget(BigDecimal.valueOf(5000 + rnd.nextInt(20) * 1000L));
        return projects.save(p);
    }

    private Project discuss(String name, String note) {
        Project p = base(name, null, ProjectStatus.DISCUSS);
        p.setNote(note);
        return projects.save(p);
    }

    /** dueInDays — срок относительно сегодня (минус = в прошлом). DONE-задачи закрыты за день до срока. */
    private void task(String title, Project p, User who, int dueInDays, Priority prio, TaskStatus status) {
        Task t = new Task();
        t.setTitle(title);
        t.setProject(p);
        t.setAssignee(who);
        t.setDueDate(today.plusDays(dueInDays));
        t.setPriority(prio);
        t.setStatus(status);
        t.setCreatedAt(now.minusDays(30 + rnd.nextInt(20)));
        if (status == TaskStatus.DONE) {
            LocalDateTime done = today.plusDays(Math.min(dueInDays, 0)).atTime(16, 0).minusDays(1);
            t.setCompletedAt(done.isAfter(now) ? now.minusHours(1) : done);
        }
        tasks.save(t);
    }

    /** Задача закрытого проекта: закрыта за пару дней до конца проекта. */
    private void doneTask(String title, Project p, User who) {
        Task t = new Task();
        t.setTitle(title);
        t.setProject(p);
        t.setAssignee(who);
        t.setDueDate(p.getEndDate());
        t.setPriority(Priority.MID);
        t.setStatus(TaskStatus.DONE);
        t.setCreatedAt(p.getStartDate().atTime(10, 0));
        LocalDateTime done = p.getEndDate().minusDays(rnd.nextInt(3)).atTime(15, 0);
        if (done.isBefore(p.getStartDate().atStartOfDay())) done = p.getEndDate().atTime(15, 0);
        t.setCompletedAt(done.isAfter(now) ? now.minusHours(1) : done);
        tasks.save(t);
    }

    /** Рабочий день: 6.5–9.5 часов, 2–3 сессии по проектам, над которыми человек работал в эту дату. */
    private List<TimeLog> day(User u, LocalDate d, List<Project> candidates) {
        List<Project> active = new ArrayList<>();
        for (Project p : candidates) {
            boolean started = p.getStartDate() == null || !d.isBefore(p.getStartDate());
            boolean notEnded = p.getEndDate() == null || !d.isAfter(p.getEndDate());
            if (started && notEnded) active.add(p);
        }
        String fallback = "Code review";
        long totalSec = (long) ((6.5 + rnd.nextDouble() * 3) * 3600);
        LocalDateTime cursor = d.atTime(9, rnd.nextInt(30));
        if (d.equals(today)) {
            long available = Duration.between(cursor, now).getSeconds();
            if (available <= 0) return List.of();
            totalSec = Math.min(totalSec, available);
        }
        int sessions = 2 + rnd.nextInt(2);
        List<TimeLog> res = new ArrayList<>();
        for (int i = 0; i < sessions && totalSec > 60; i++) {
            long len = i == sessions - 1 ? totalSec : totalSec / (sessions - i) + rnd.nextInt(1800) - 900;
            len = Math.max(600, Math.min(len, totalSec));
            String label = active.isEmpty() ? fallback : active.get(rnd.nextInt(active.size())).getName();
            res.add(new TimeLog(u, cursor, len, label));
            cursor = cursor.plusSeconds(len + 20 * 60L); // перерыв 20 минут
            totalSec -= len;
            if (d.equals(today) && cursor.isAfter(now)) break;
        }
        return res;
    }

    private void payout(User u, BigDecimal amount, String label, LocalDate date, YearMonth period, String batch) {
        Payout p = new Payout();
        p.setUser(u);
        p.setAmount(Fmt.money(amount));
        p.setLabel(label);
        p.setDate(date);
        p.setStatus(PayoutStatus.PAID);
        p.setPeriod(period == null ? null : period.toString());
        p.setBatchId(batch);
        payouts.save(p);
        u.setWalletBalance(u.getWalletBalance().add(Fmt.money(amount)));
        personalTx(u, date.atTime(9, 0), COMPANY + " Inc.", label, TxDirection.IN, amount, TxCategory.SALARY,
                TxStatus.COMPLETED);
    }

    private void personalTx(User u, LocalDateTime at, String name, String note, TxDirection dir, BigDecimal amount,
                            TxCategory cat, TxStatus status) {
        PaypalTransaction t = tx(at, name, note, dir, amount, BigDecimal.ZERO, cat, u.getDepartment(), status);
        t.setOwner(u);
        txs.save(t);
    }

    private void companyTx(LocalDateTime at, String name, String note, TxDirection dir, BigDecimal amount,
                           BigDecimal fee, TxCategory cat, String dept, TxStatus status) {
        if (at.isAfter(now)) return; // будущее не пишем
        txs.save(tx(at, name, note, dir, amount, fee, cat, dept, status));
    }

    private PaypalTransaction tx(LocalDateTime at, String name, String note, TxDirection dir, BigDecimal amount,
                                 BigDecimal fee, TxCategory cat, String dept, TxStatus status) {
        PaypalTransaction t = new PaypalTransaction();
        t.setId(Ids.paypal());
        t.setCompany(COMPANY);
        t.setDateTime(at);
        t.setName(name);
        t.setNote(note);
        t.setDirection(dir);
        t.setAmount(Fmt.money(amount));
        t.setFee(Fmt.money(fee));
        t.setStatus(status);
        t.setCategory(cat);
        t.setDepartment(dept);
        return t;
    }

    private void sched(String label, String amount, LocalDate due, String kind, String by, boolean approved) {
        ScheduledPayment s = new ScheduledPayment();
        s.setCompany(COMPANY);
        s.setLabel(label);
        s.setAmount(new BigDecimal(amount));
        s.setDueDate(due);
        s.setKind(kind);
        s.setRequestedBy(by);
        s.setApproved(approved);
        scheduled.save(s);
    }

    private void chat(User u, String question) {
        ChatMessage c = new ChatMessage();
        c.setUser(u);
        c.setQuestion(question);
        c.setReply("(seeded)");
        c.setCreatedAt(now.minusDays(1 + rnd.nextInt(20)));
        chats.save(c);
    }

    private LocalDateTime at(YearMonth ym, int day, int h, int min) {
        return ym.atDay(Math.min(day, ym.lengthOfMonth())).atTime(h, min);
    }

    /** Чтобы "закрыто в этом месяце" не уехало в прошлый месяц в первых числах. */
    private LocalDate clampToMonth(LocalDate d) {
        LocalDate first = month.atDay(1);
        return d.isBefore(first) ? first : d;
    }
}
