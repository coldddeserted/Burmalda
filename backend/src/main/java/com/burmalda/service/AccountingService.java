package com.burmalda.service;

import com.burmalda.domain.*;
import com.burmalda.repo.*;
import com.burmalda.util.Errors;
import com.burmalda.util.Fmt;
import com.burmalda.util.Ids;
import com.burmalda.web.dto.AccountantViews.*;
import com.burmalda.web.dto.CommonViews.MonthValue;
import com.burmalda.web.dto.Requests;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

/** Всё, что делает бухгалтер: зарплаты (Mass Pay), финансы, платежи, одобрения. */
@Service
@Transactional
public class AccountingService {

    private final PayrollService payroll;
    private final BudgetService budgets;
    private final PaypalClient paypal;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final PayoutRepository payouts;
    private final PaypalTransactionRepository txs;
    private final ScheduledPaymentRepository scheduled;
    private final CompanyAccountRepository accounts;

    public AccountingService(PayrollService payroll, BudgetService budgets, PaypalClient paypal,
                             UserRepository users, ProjectRepository projects, PayoutRepository payouts,
                             PaypalTransactionRepository txs, ScheduledPaymentRepository scheduled,
                             CompanyAccountRepository accounts) {
        this.payroll = payroll;
        this.budgets = budgets;
        this.paypal = paypal;
        this.users = users;
        this.projects = projects;
        this.payouts = payouts;
        this.txs = txs;
        this.scheduled = scheduled;
        this.accounts = accounts;
    }

    /* ======================= Tasks бухгалтера ======================= */

    @Transactional(readOnly = true)
    public Team team(User me) {
        YearMonth m = YearMonth.now();
        List<EmployeePay> employees = payroll.companyMonth(me.getCompany(), m).stream()
                .map(e -> new EmployeePay(e.user().getId(), e.user().getName(), e.user().getDepartment(),
                        e.tasksDone(), e.tasksTotal(), e.projectsEnded(), e.hours(), e.salary(),
                        e.paid() ? "Paid" : "Unpaid"))
                .toList();
        List<EndedProject> ended = projects.findByCompany(me.getCompany()).stream()
                .filter(p -> p.getStatus() == ProjectStatus.ENDED && p.getEndDate() != null
                        && YearMonth.from(p.getEndDate()).equals(m))
                .sorted(Comparator.comparing(Project::getEndDate).reversed())
                .map(p -> new EndedProject(p.getName(), p.getLead() == null ? "—" : p.getLead().getName(),
                        Fmt.date(p.getEndDate()), p.getBudget()))
                .toList();
        return new Team(Fmt.monthYear(m), employees, ended);
    }

    /**
     * Выплатить зарплату за текущий месяц выбранным сотрудникам одной пачкой PayPal Mass Pay.
     * Уже оплаченные пропускаются, сумма считается на сервере (фронту не доверяем).
     */
    public PayResult pay(User me, List<Long> ids) {
        YearMonth m = YearMonth.now();
        CompanyAccount acc = account(me.getCompany());

        List<PayrollService.EmployeeMonth> toPay = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(ids)) {
            User u = users.findById(id)
                    .filter(x -> x.getRole() == Role.EMPLOYEE && x.getCompany().equals(me.getCompany()))
                    .orElseThrow(() -> Errors.notFound("Сотрудник " + id + " не найден"));
            PayrollService.EmployeeMonth em = payroll.monthFor(u, m);
            if (!em.paid() && em.salary().signum() > 0) toPay.add(em);
        }
        if (toPay.isEmpty()) {
            throw Errors.badRequest("Выбранным сотрудникам нечего платить за " + Fmt.monthYear(m));
        }

        BigDecimal total = PayrollService.total(toPay);
        BigDecimal fee = Fmt.money(total.multiply(PayrollService.MASS_PAY_FEE_RATE));
        if (acc.getBalance().compareTo(total.add(fee)) < 0) {
            throw Errors.badRequest("Недостаточно средств на PayPal: нужно $" + total.add(fee)
                    + ", доступно $" + acc.getBalance());
        }

        String label = Fmt.monthLong(m) + " salary";
        List<PaypalClient.Item> items = toPay.stream()
                .map(e -> new PaypalClient.Item(paypalEmail(e.user()), e.salary(), label))
                .toList();
        PaypalClient.Batch batch = paypal.payout(items, "Your " + label + " from " + me.getCompany());

        LocalDateTime now = LocalDateTime.now();
        for (PayrollService.EmployeeMonth e : toPay) {
            User u = e.user();
            Payout p = new Payout();
            p.setUser(u);
            p.setAmount(e.salary());
            p.setLabel(label);
            p.setDate(now.toLocalDate());
            p.setStatus(PayoutStatus.PAID);
            p.setPeriod(m.toString());
            p.setBatchId(batch.batchId());
            payouts.save(p);

            u.setWalletBalance(u.getWalletBalance().add(e.salary()));
            txs.save(tx(me.getCompany(), u, now, me.getCompany(), label, TxDirection.IN, e.salary(),
                    BigDecimal.ZERO, TxCategory.SALARY, u.getDepartment()));
        }

        acc.setBalance(acc.getBalance().subtract(total).subtract(fee));
        txs.save(tx(me.getCompany(), null, now, "Mass Pay: " + toPay.size() + " recipients",
                "Payroll " + Fmt.monthYear(m) + ", batch " + batch.batchId(), TxDirection.OUT, total, fee,
                TxCategory.PAYROLL, null));

        return new PayResult(true, batch.batchId(), toPay.size(), total, batch.simulated());
    }

    /* ======================= Finance ======================= */

    @Transactional(readOnly = true)
    public Finance finance(User me) {
        String company = me.getCompany();
        YearMonth m = YearMonth.now();
        CompanyAccount acc = account(company);
        List<PayrollService.EmployeeMonth> cur = payroll.companyMonth(company, m);
        BigDecimal unpaidPayroll = PayrollService.unpaidTotal(cur);

        List<Upcoming> upcoming = new ArrayList<>();
        if (unpaidPayroll.signum() > 0) {
            upcoming.add(new Upcoming("Payroll run (Mass Pay)", Fmt.date(PayrollService.payday(m)),
                    unpaidPayroll, "Payroll"));
        }
        BigDecimal reserved = unpaidPayroll;
        for (ScheduledPayment s : scheduled.findByCompanyOrderByDueDateAsc(company)) {
            upcoming.add(new Upcoming(s.getLabel(), Fmt.date(s.getDueDate()), s.getAmount(), s.getKind()));
            reserved = reserved.add(s.getAmount());
        }
        upcoming.sort(Comparator.comparing(u -> LocalDate.parse(u.date(),
                java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))));

        List<MonthValue> months = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = m.minusMonths(i);
            months.add(new MonthValue(Fmt.monthShort(ym), PayrollService.total(payroll.companyMonth(company, ym))));
        }

        return new Finance(Fmt.money(acc.getBalance()), Fmt.money(acc.getBalance().subtract(reserved)),
                Fmt.money(reserved), "USD", acc.getBankLabel(), upcoming,
                budgets.allocation(company, m, cur), months);
    }

    /** Пополнить PayPal компании с банковского счёта. */
    public BigDecimal addFunds(User me, BigDecimal amount) {
        CompanyAccount acc = account(me.getCompany());
        BigDecimal a = Fmt.money(amount);
        acc.setBalance(acc.getBalance().add(a));
        txs.save(tx(me.getCompany(), null, LocalDateTime.now(), "Funding from bank", acc.getBankLabel(),
                TxDirection.IN, a, BigDecimal.ZERO, TxCategory.FUNDING, null));
        return acc.getBalance();
    }

    /** Разовый платёж: to — email (уйдёт в PayPal) или просто имя получателя. */
    public BigDecimal sendPayment(User me, Requests.Payment r) {
        CompanyAccount acc = account(me.getCompany());
        BigDecimal a = Fmt.money(r.amount());
        if (acc.getBalance().compareTo(a) < 0) throw Errors.badRequest("Недостаточно средств на PayPal");

        String to = r.to().trim();
        String note = r.note() == null || r.note().isBlank() ? "Payment" : r.note().trim();
        if (to.contains("@")) {
            paypal.payout(List.of(new PaypalClient.Item(to, a, note)), "Payment from " + me.getCompany());
        }
        acc.setBalance(acc.getBalance().subtract(a));
        txs.save(tx(me.getCompany(), null, LocalDateTime.now(), to, note, TxDirection.OUT, a, BigDecimal.ZERO,
                TxCategory.VENDOR, null));
        return acc.getBalance();
    }

    /** Одобрить запланированный платёж: деньги уходят сразу, строка пропадает из Pending Approvals. */
    public BigDecimal approve(User me, Long id) {
        ScheduledPayment s = scheduled.findById(id)
                .filter(x -> x.getCompany().equals(me.getCompany()))
                .orElseThrow(() -> Errors.notFound("Платёж не найден"));
        CompanyAccount acc = account(me.getCompany());
        if (acc.getBalance().compareTo(s.getAmount()) < 0) throw Errors.badRequest("Недостаточно средств на PayPal");

        acc.setBalance(acc.getBalance().subtract(s.getAmount()));
        txs.save(tx(me.getCompany(), null, LocalDateTime.now(), s.getLabel(),
                "Approved by " + me.getName(), TxDirection.OUT, s.getAmount(), BigDecimal.ZERO,
                category(s.getKind()), null));
        scheduled.delete(s);
        return acc.getBalance();
    }

    /* ======================= helpers ======================= */

    private CompanyAccount account(String company) {
        return accounts.findByCompany(company).orElseGet(() -> accounts.save(new CompanyAccount(company)));
    }

    private static String paypalEmail(User u) {
        return u.getPaypalEmail() == null || u.getPaypalEmail().isBlank() ? u.getEmail() : u.getPaypalEmail();
    }

    private static TxCategory category(String kind) {
        if (kind == null) return TxCategory.EXPENSE;
        return switch (kind.toLowerCase()) {
            case "payroll" -> TxCategory.PAYROLL;
            case "tax" -> TxCategory.TAX;
            case "vendor" -> TxCategory.VENDOR;
            case "transfer" -> TxCategory.TRANSFER;
            default -> TxCategory.EXPENSE;
        };
    }

    static PaypalTransaction tx(String company, User owner, LocalDateTime at, String name, String note,
                                TxDirection dir, BigDecimal amount, BigDecimal fee, TxCategory cat, String dept) {
        PaypalTransaction t = new PaypalTransaction();
        t.setId(Ids.paypal());
        t.setCompany(company);
        t.setOwner(owner);
        t.setDateTime(at);
        t.setName(name);
        t.setNote(note);
        t.setDirection(dir);
        t.setAmount(Fmt.money(amount));
        t.setFee(Fmt.money(fee));
        t.setStatus(TxStatus.COMPLETED);
        t.setCategory(cat);
        t.setDepartment(dept);
        return t;
    }
}
