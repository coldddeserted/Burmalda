package com.burmalda.service;

import com.burmalda.domain.CompanyAccount;
import com.burmalda.domain.PaypalTransaction;
import com.burmalda.domain.Role;
import com.burmalda.domain.User;
import com.burmalda.repo.CompanyAccountRepository;
import com.burmalda.repo.PaypalTransactionRepository;
import com.burmalda.util.Fmt;
import com.burmalda.web.dto.CommonViews.TxList;
import com.burmalda.web.dto.CommonViews.TxRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** GET /api/paypal/transactions: бухгалтеру — счёт компании, сотруднику — его кошелёк. */
@Service
@Transactional(readOnly = true)
public class TransactionService {

    private final PaypalTransactionRepository txs;
    private final CompanyAccountRepository accounts;

    public TransactionService(PaypalTransactionRepository txs, CompanyAccountRepository accounts) {
        this.txs = txs;
        this.accounts = accounts;
    }

    public TxList list(User me) {
        if (me.getRole() == Role.ACCOUNTANT) {
            BigDecimal balance = accounts.findByCompany(me.getCompany())
                    .map(CompanyAccount::getBalance).orElse(BigDecimal.ZERO);
            return new TxList(Fmt.money(balance),
                    rows(txs.findByCompanyAndOwnerIsNullOrderByDateTimeDesc(me.getCompany())));
        }
        return new TxList(Fmt.money(me.getWalletBalance()), rows(txs.findByOwnerOrderByDateTimeDesc(me)));
    }

    private static List<TxRow> rows(List<PaypalTransaction> list) {
        return list.stream().map(t -> new TxRow(t.getId(), Fmt.tx(t.getDateTime()), t.getName(),
                t.getNote() == null ? "" : t.getNote(), t.getDirection().api(), t.getAmount(),
                t.getFee(), t.getStatus().api())).toList();
    }
}
