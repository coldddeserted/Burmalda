package com.burmalda.service;

import com.burmalda.domain.*;
import com.burmalda.repo.PayoutRepository;
import com.burmalda.repo.PaypalTransactionRepository;
import com.burmalda.repo.UserRepository;
import com.burmalda.util.Errors;
import com.burmalda.util.Fmt;
import com.burmalda.web.dto.CommonViews.MonthValue;
import com.burmalda.web.dto.CommonViews.PayoutRow;
import com.burmalda.web.dto.EmployeeViews.PaypalAccount;
import com.burmalda.web.dto.EmployeeViews.Wallet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class WalletService {

    private final PayrollService payroll;
    private final PayoutRepository payouts;
    private final PaypalTransactionRepository txs;
    private final UserRepository users;

    public WalletService(PayrollService payroll, PayoutRepository payouts, PaypalTransactionRepository txs,
                         UserRepository users) {
        this.payroll = payroll;
        this.payouts = payouts;
        this.txs = txs;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Wallet wallet(User me) {
        YearMonth m = YearMonth.now();
        PayrollService.EmployeeMonth cur = payroll.monthFor(me, m);
        List<Payout> all = payouts.findByUserOrderByDateDesc(me);

        List<MonthValue> months = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = m.minusMonths(i);
            BigDecimal v = all.stream()
                    .filter(p -> p.getStatus() == PayoutStatus.PAID && YearMonth.from(p.getDate()).equals(ym))
                    .map(Payout::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            months.add(new MonthValue(Fmt.monthShort(ym), v));
        }

        List<PayoutRow> history = all.stream().limit(10)
                .map(p -> new PayoutRow(Fmt.date(p.getDate()), p.getLabel(), p.getAmount(), p.getStatus().api()))
                .toList();

        String email = me.getPaypalEmail() == null ? me.getEmail() : me.getPaypalEmail();
        return new Wallet(Fmt.money(me.getWalletBalance()), "USD", cur.paid() ? BigDecimal.ZERO : cur.salary(),
                Fmt.date(PayrollService.payday(m)), new PaypalAccount(email, me.isPaypalVerified()),
                months, history);
    }

    /** Вывод с PayPal на банк. Сам PayPal API вывод на банк не даёт — фиксируем у себя. */
    public BigDecimal withdraw(User me, BigDecimal amount) {
        BigDecimal a = Fmt.money(amount);
        if (a.compareTo(me.getWalletBalance()) > 0) {
            throw Errors.badRequest("Недостаточно средств: доступно $" + Fmt.money(me.getWalletBalance()));
        }
        me.setWalletBalance(me.getWalletBalance().subtract(a));
        users.save(me); // me пришёл из контроллера (вне транзакции) — сохраняем явно
        txs.save(AccountingService.tx(me.getCompany(), me, LocalDateTime.now(), "Withdrawal to bank",
                "Bank account linked to PayPal", TxDirection.OUT, a, BigDecimal.ZERO, TxCategory.WITHDRAWAL,
                me.getDepartment()));
        return me.getWalletBalance();
    }
}
