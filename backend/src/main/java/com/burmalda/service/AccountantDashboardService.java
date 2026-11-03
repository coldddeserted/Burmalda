package com.burmalda.service;

import com.burmalda.domain.*;
import com.burmalda.repo.ComplianceItemRepository;
import com.burmalda.repo.PaypalTransactionRepository;
import com.burmalda.repo.ScheduledPaymentRepository;
import com.burmalda.util.Fmt;
import com.burmalda.web.dto.AccountantViews.*;
import com.burmalda.web.dto.CommonViews.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** GET /api/dashboard/accountant — финансовая картина компании. */
@Service
@Transactional(readOnly = true)
public class AccountantDashboardService {

    private static final int CASHFLOW_WEEKS = 10;

    private final PayrollService payroll;
    private final BudgetService budgets;
    private final AnomalyService anomalies;
    private final PaypalTransactionRepository txs;
    private final ScheduledPaymentRepository scheduled;
    private final ComplianceItemRepository compliance;

    public AccountantDashboardService(PayrollService payroll, BudgetService budgets, AnomalyService anomalies,
                                      PaypalTransactionRepository txs, ScheduledPaymentRepository scheduled,
                                      ComplianceItemRepository compliance) {
        this.payroll = payroll;
        this.budgets = budgets;
        this.anomalies = anomalies;
        this.txs = txs;
        this.scheduled = scheduled;
        this.compliance = compliance;
    }

    public Dashboard build(User me) {
        String company = me.getCompany();
        YearMonth month = YearMonth.now();
        YearMonth prev = month.minusMonths(1);

        List<PayrollService.EmployeeMonth> cur = payroll.companyMonth(company, month);
        BigDecimal curTotal = PayrollService.total(cur);
        BigDecimal prevTotal = PayrollService.total(payroll.companyMonth(company, prev));

        List<PendingRow> pending = pendingRows(company);
        BigDecimal pendingSum = pending.stream().map(PendingRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<AllocRow> alloc = budgets.allocation(company, month, cur);
        List<AnomalyRow> flagged = anomalies.detect(company);
        List<DocRow> docs = docs(company);
        List<PaypalTransaction> companyTx = txs.findByCompanyAndOwnerIsNullOrderByDateTimeDesc(company);
        List<SavingRow> savingRows = savings(companyTx, month);
        BigDecimal savingTotal = sum(savingRows);
        BigDecimal savingPrev = sum(savings(companyTx, prev));

        return new Dashboard(
                new Payroll(curTotal, "USD", Fmt.deltaPct(curTotal, prevTotal), prevTotal,
                        Fmt.monthShort(prev), payroll.weeklyPayrollK(company, month)),
                new Money(Fmt.money(pendingSum), "USD"),
                new Budget(Fmt.money(BudgetService.available(alloc)), "USD", splits(alloc)),
                new Risk(flagged.size()),
                compliance(docs),
                cashFlow(companyTx, month),
                companyTx.stream().limit(6).map(AccountantDashboardService::dashTx).toList(),
                alloc.stream().map(a -> new DeptAmount(a.dept(), a.spent()))
                        .sorted(Comparator.comparing(DeptAmount::amount).reversed()).toList(),
                new Savings(savingTotal, Fmt.deltaPct(savingTotal, savingPrev), savingWeeks(companyTx, month)),
                new Details(pending, alloc, flagged, docs, savingRows));
    }

    public List<PendingRow> pendingRows(String company) {
        return scheduled.findByCompanyAndApprovedFalseOrderByDueDateAsc(company).stream()
                .map(p -> new PendingRow(p.getId(), p.getLabel(), p.getAmount(), p.getRequestedBy(),
                        Fmt.date(p.getDueDate())))
                .toList();
    }

    /** Чек-лист документов: профили сотрудников (верифицирован ли PayPal) + пункты из БД. */
    public List<DocRow> docs(String company) {
        List<User> emps = payroll.employees(company);
        long verified = emps.stream().filter(User::isPaypalVerified).count();
        List<DocRow> res = new ArrayList<>();
        res.add(new DocRow("Employee profiles", verified + " / " + emps.size(), verified == emps.size()));
        for (ComplianceItem c : compliance.findByCompany(company)) {
            res.add(new DocRow(c.getName(), c.getStatus(), c.isOk()));
        }
        return res;
    }

    private Compliance compliance(List<DocRow> docs) {
        long ok = docs.stream().filter(DocRow::ok).count();
        int percent = docs.isEmpty() ? 100 : (int) Math.round(ok * 100.0 / docs.size());
        List<String> items = docs.stream().limit(2).map(d -> d.name() + " (" + d.status() + ")").toList();
        return new Compliance(percent, items);
    }

    /** Три суммы под "Available Budget": остаток двух самых крупных отделов и всех остальных. */
    private static List<BigDecimal> splits(List<AllocRow> alloc) {
        List<BigDecimal> left = alloc.stream()
                .map(a -> a.allocated().subtract(a.spent()).max(BigDecimal.ZERO))
                .sorted(Comparator.reverseOrder()).toList();
        BigDecimal s1 = left.size() > 0 ? left.get(0) : BigDecimal.ZERO;
        BigDecimal s2 = left.size() > 1 ? left.get(1) : BigDecimal.ZERO;
        BigDecimal s3 = left.stream().skip(2).reduce(BigDecimal.ZERO, BigDecimal::add);
        return List.of(Fmt.money(s1), Fmt.money(s2), Fmt.money(s3));
    }

    /**
     * Денежный поток за 10 недель (в $k) + прогноз.
     * Прогноз — скользящее среднее притока за 3 предыдущие недели (простая, но честная модель).
     */
    public CashFlow cashFlow(List<PaypalTransaction> companyTx, YearMonth month) {
        LocalDate thisMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate start = thisMonday.minusWeeks(CASHFLOW_WEEKS - 1);
        double[] in = new double[CASHFLOW_WEEKS];
        double[] out = new double[CASHFLOW_WEEKS];
        for (PaypalTransaction t : companyTx) {
            if (t.getStatus() != TxStatus.COMPLETED) continue;
            LocalDate d = t.getDateTime().toLocalDate();
            if (d.isBefore(start)) continue;
            int w = (int) ((d.toEpochDay() - start.toEpochDay()) / 7);
            if (w < 0 || w >= CASHFLOW_WEEKS) continue;
            if (t.getDirection() == TxDirection.IN) in[w] += t.getAmount().doubleValue();
            else out[w] += t.getAmount().add(t.getFee()).doubleValue();
        }
        List<Double> inflow = new ArrayList<>(), outflow = new ArrayList<>(), forecast = new ArrayList<>();
        for (int i = 0; i < CASHFLOW_WEEKS; i++) {
            inflow.add(k(in[i]));
            outflow.add(k(out[i]));
            int from = Math.max(0, i - 3);
            double avg = 0;
            for (int j = from; j < i; j++) avg += in[j];
            avg = i == from ? in[i] : avg / (i - from);
            forecast.add(k(avg));
        }

        double[] bin = new double[4], bout = new double[4];
        for (PaypalTransaction t : companyTx) {
            if (t.getStatus() != TxStatus.COMPLETED || !YearMonth.from(t.getDateTime()).equals(month)) continue;
            int w = PayrollService.weekOfMonth(t.getDateTime().toLocalDate());
            if (t.getDirection() == TxDirection.IN) bin[w] += t.getAmount().doubleValue();
            else bout[w] += t.getAmount().doubleValue();
        }
        List<InOut> bars = new ArrayList<>();
        for (int i = 0; i < 4; i++) bars.add(new InOut(k(bin[i]), k(bout[i])));
        return new CashFlow(inflow, outflow, forecast, bars);
    }

    /**
     * Экономия: Mass Pay вместо поштучных выплат (2% - 1% от суммы пачки)
     * и маршрутизация валютных переводов (условно 1% от суммы).
     */
    public List<SavingRow> savings(List<PaypalTransaction> companyTx, YearMonth month) {
        BigDecimal massPay = BigDecimal.ZERO, fx = BigDecimal.ZERO;
        BigDecimal diff = PayrollService.SINGLE_PAYOUT_FEE_RATE.subtract(PayrollService.MASS_PAY_FEE_RATE);
        for (PaypalTransaction t : companyTx) {
            if (t.getStatus() != TxStatus.COMPLETED || !YearMonth.from(t.getDateTime()).equals(month)) continue;
            if (t.getCategory() == TxCategory.PAYROLL) massPay = massPay.add(t.getAmount().multiply(diff));
            if (t.getCategory() == TxCategory.TRANSFER) fx = fx.add(t.getAmount().multiply(new BigDecimal("0.01")));
        }
        return List.of(
                new SavingRow("Mass Pay instead of single payouts", Fmt.money(massPay)),
                new SavingRow("Smart FX routing", Fmt.money(fx)));
    }

    private List<Integer> savingWeeks(List<PaypalTransaction> companyTx, YearMonth month) {
        double[] w = new double[4];
        for (PaypalTransaction t : companyTx) {
            boolean counts = t.getCategory() == TxCategory.PAYROLL || t.getCategory() == TxCategory.TRANSFER;
            if (counts && t.getStatus() == TxStatus.COMPLETED && YearMonth.from(t.getDateTime()).equals(month)) {
                w[PayrollService.weekOfMonth(t.getDateTime().toLocalDate())] += t.getAmount().doubleValue();
            }
        }
        double max = Math.max(1, Math.max(Math.max(w[0], w[1]), Math.max(w[2], w[3])));
        List<Integer> res = new ArrayList<>();
        for (double v : w) res.add((int) Math.round(v / max * 100));
        return res;
    }

    private static DashTx dashTx(PaypalTransaction t) {
        TxCategory c = t.getCategory() == null ? TxCategory.EXPENSE : t.getCategory();
        return new DashTx(t.getId(), t.getName(), t.getAmount(), Fmt.dashTx(t.getDateTime()),
                t.getStatus().api(), c.badge, c.color);
    }

    private static BigDecimal sum(List<SavingRow> rows) {
        return Fmt.money(rows.stream().map(SavingRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static double k(double v) {
        return Fmt.round1(v / 1000);
    }
}
