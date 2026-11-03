package com.burmalda.service;

import com.burmalda.domain.*;
import com.burmalda.repo.DepartmentBudgetRepository;
import com.burmalda.repo.PaypalTransactionRepository;
import com.burmalda.web.dto.AccountantViews.AllocRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Department budgets for a month.
 * Spent = salaries of the department (from PayrollService) + company expenses tagged with this department.
 */
@Service
@Transactional(readOnly = true)
public class BudgetService {

    private final DepartmentBudgetRepository budgets;
    private final PaypalTransactionRepository txs;
    private final PayrollService payroll;

    public BudgetService(DepartmentBudgetRepository budgets, PaypalTransactionRepository txs,
                         PayrollService payroll) {
        this.budgets = budgets;
        this.txs = txs;
        this.payroll = payroll;
    }

    public List<AllocRow> allocation(String company, YearMonth m, List<PayrollService.EmployeeMonth> month) {
        List<PaypalTransaction> companyTx = txs.findByCompanyAndOwnerIsNullOrderByDateTimeDesc(company);
        List<AllocRow> rows = new ArrayList<>();
        for (DepartmentBudget b : budgets.findByCompanyOrderByAllocatedDesc(company)) {
            String dept = b.getDepartment();

            BigDecimal salaries = BigDecimal.ZERO;
            int heads = 0;
            for (PayrollService.EmployeeMonth e : month) {
                if (dept.equalsIgnoreCase(e.user().getDepartment())) {
                    salaries = salaries.add(e.salary());
                    heads++;
                }
            }

            BigDecimal expenses = BigDecimal.ZERO;
            for (PaypalTransaction t : companyTx) {
                boolean thisMonth = YearMonth.from(t.getDateTime()).equals(m);
                if (thisMonth && t.getDirection() == TxDirection.OUT && t.getStatus() == TxStatus.COMPLETED
                        && t.getCategory() != TxCategory.PAYROLL // salaries are already counted above
                        && dept.equalsIgnoreCase(t.getDepartment())) {
                    expenses = expenses.add(t.getAmount());
                }
            }
            rows.add(new AllocRow(dept, b.getAllocated(), salaries.add(expenses), heads));
        }
        return rows;
    }

    public List<AllocRow> allocation(String company, YearMonth m) {
        return allocation(company, m, payroll.companyMonth(company, m));
    }

    /** Budget left over all departments (negative ones are ignored). */
    public static BigDecimal available(List<AllocRow> alloc) {
        return alloc.stream()
                .map(a -> a.allocated().subtract(a.spent()).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
