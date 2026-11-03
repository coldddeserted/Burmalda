package com.burmalda.web;

import com.burmalda.security.AuthUser;
import com.burmalda.service.AccountantDashboardService;
import com.burmalda.service.AccountingService;
import com.burmalda.service.PaypalClient;
import com.burmalda.repo.PayoutRepository;
import com.burmalda.domain.Payout;
import com.burmalda.util.Errors;
import com.burmalda.service.UserService;
import com.burmalda.web.dto.AccountantViews.Finance;
import com.burmalda.web.dto.AccountantViews.PayResult;
import com.burmalda.web.dto.AccountantViews.PendingRow;
import com.burmalda.web.dto.AccountantViews.Team;
import com.burmalda.web.dto.CommonViews.OkBalance;
import com.burmalda.web.dto.Requests;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Только для роли ACCOUNTANT (ограничение в SecurityConfig). */
@RestController
@RequestMapping("/api/accounting")
public class AccountingController {

    private final UserService users;
    private final AccountingService accounting;
    private final AccountantDashboardService dashboard;
    private final PaypalClient paypal;
    private final PayoutRepository payouts;

    public AccountingController(UserService users, AccountingService accounting,
                                AccountantDashboardService dashboard, PaypalClient paypal,
                                PayoutRepository payouts) {
        this.users = users;
        this.accounting = accounting;
        this.dashboard = dashboard;
        this.paypal = paypal;
        this.payouts = payouts;
    }

    /** Did the money arrive? Asks PayPal about a payout batch. "latest" = the company's last batch. */
    @GetMapping("/payouts/{batchId}/status")
    public PaypalClient.BatchStatus payoutStatus(@AuthenticationPrincipal AuthUser me, @PathVariable String batchId) {
        String company = users.require(me).getCompany();
        String id = "latest".equals(batchId)
                ? payouts.findFirstByUser_CompanyAndBatchIdNotNullOrderByIdDesc(company)
                        .map(Payout::getBatchId).orElseThrow(() -> Errors.notFound("No payouts yet"))
                : batchId;
        return paypal.batchStatus(id);
    }

    @GetMapping("/team")
    public Team team(@AuthenticationPrincipal AuthUser me) {
        return accounting.team(users.require(me));
    }

    /** PayPal Mass Pay выбранным сотрудникам. */
    @PostMapping("/pay")
    public PayResult pay(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody Requests.Pay body) {
        return accounting.pay(users.require(me), body.ids());
    }

    @GetMapping("/finance")
    public Finance finance(@AuthenticationPrincipal AuthUser me) {
        return accounting.finance(users.require(me));
    }

    @PostMapping("/funds")
    public OkBalance addFunds(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody Requests.Amount body) {
        return new OkBalance(true, accounting.addFunds(users.require(me), body.amount()));
    }

    @PostMapping("/payment")
    public OkBalance payment(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody Requests.Payment body) {
        return new OkBalance(true, accounting.sendPayment(users.require(me), body));
    }

    @GetMapping("/approvals")
    public List<PendingRow> approvals(@AuthenticationPrincipal AuthUser me) {
        return dashboard.pendingRows(users.require(me).getCompany());
    }

    @PostMapping("/approvals/{id}/approve")
    public OkBalance approve(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return new OkBalance(true, accounting.approve(users.require(me), id));
    }
}
