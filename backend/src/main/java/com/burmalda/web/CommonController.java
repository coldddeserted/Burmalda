package com.burmalda.web;

import com.burmalda.security.AuthUser;
import com.burmalda.service.*;
import com.burmalda.web.dto.CommonViews.*;
import com.burmalda.web.dto.Requests;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Общие для обеих ролей: AI, транзакции PayPal, отчёты, поиск. */
@RestController
@RequestMapping("/api")
public class CommonController {

    private final UserService users;
    private final AiService ai;
    private final TransactionService transactions;
    private final ReportService reports;
    private final SearchService search;

    public CommonController(UserService users, AiService ai, TransactionService transactions,
                            ReportService reports, SearchService search) {
        this.users = users;
        this.ai = ai;
        this.transactions = transactions;
        this.reports = reports;
        this.search = search;
    }

    /* ---------- AI Burmalda (параметр role фронт шлёт, но роль берём из токена) ---------- */

    @PostMapping("/ai/chat")
    public ChatReply chat(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody Requests.Chat body) {
        return new ChatReply(ai.chat(users.require(me), body.message()));
    }

    @GetMapping("/ai/suggestions")
    public List<String> suggestions(@AuthenticationPrincipal AuthUser me) {
        return ai.suggestions(users.require(me));
    }

    @GetMapping("/ai/history")
    public List<String> history(@AuthenticationPrincipal AuthUser me) {
        return ai.history(users.require(me));
    }

    @GetMapping("/ai/summary")
    public AiSummary summary(@AuthenticationPrincipal AuthUser me) {
        return ai.summary(users.require(me));
    }

    /* ---------- PayPal ---------- */

    @GetMapping("/paypal/transactions")
    public TxList transactions(@AuthenticationPrincipal AuthUser me) {
        return transactions.list(users.require(me));
    }

    /* ---------- Reports ---------- */

    @GetMapping("/reports")
    public List<ReportDef> reports(@AuthenticationPrincipal AuthUser me) {
        return reports.list(users.require(me));
    }

    @PostMapping("/reports/{id}/generate")
    public ReportResult generate(@AuthenticationPrincipal AuthUser me, @PathVariable String id,
                                 @RequestBody(required = false) Requests.Report body) {
        String period = body == null ? null : body.period();
        return reports.generate(users.require(me), id, period);
    }

    /* ---------- Search ---------- */

    @GetMapping("/search")
    public List<SearchHit> search(@AuthenticationPrincipal AuthUser me, @RequestParam(defaultValue = "") String q) {
        return search.search(users.require(me), q);
    }
}
