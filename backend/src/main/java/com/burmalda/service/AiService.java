package com.burmalda.service;

import com.burmalda.domain.*;
import com.burmalda.repo.ChatMessageRepository;
import com.burmalda.repo.CompanyAccountRepository;
import com.burmalda.repo.PayoutRepository;
import com.burmalda.repo.PaypalTransactionRepository;
import com.burmalda.repo.TaskRepository;
import com.burmalda.util.Errors;
import com.burmalda.util.Fmt;
import com.burmalda.web.dto.AccountantViews.AnomalyRow;
import com.burmalda.web.dto.AccountantViews.PendingRow;
import com.burmalda.web.dto.AccountantViews.SavingRow;
import com.burmalda.web.dto.CommonViews.AiSummary;
import com.burmalda.web.dto.CommonViews.SummaryPoint;
import com.burmalda.web.dto.Requests;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * AI Burmalda: chat, suggestions, history and the AI summary cards.
 * The chat itself is the agent in AgentService; this class adds the rule-based fallback.
 */
@Service
public class AiService {

    private final PayrollService payroll;
    private final AnomalyService anomalies;
    private final AccountantDashboardService accountantDashboard;
    private final AgentService agent;
    private final ChatMessageRepository chats;
    private final PayoutRepository payouts;
    private final TaskRepository tasks;
    private final PaypalTransactionRepository txs;
    private final CompanyAccountRepository accounts;

    public AiService(PayrollService payroll, AnomalyService anomalies,
                     AccountantDashboardService accountantDashboard, AgentService agent, ChatMessageRepository chats,
                     PayoutRepository payouts, TaskRepository tasks, PaypalTransactionRepository txs,
                     CompanyAccountRepository accounts) {
        this.payroll = payroll;
        this.anomalies = anomalies;
        this.accountantDashboard = accountantDashboard;
        this.agent = agent;
        this.chats = chats;
        this.payouts = payouts;
        this.tasks = tasks;
        this.txs = txs;
        this.accounts = accounts;
    }

    /* ======================= chat ======================= */

    /**
     * With AI_API_KEY: Gemini agent with tools (AgentService) — reads payroll, PayPal transactions,
     * risk alerts and can pay salaries through PayPal Payouts after the user confirms.
     * Without a key or if the AI call fails: rule-based answers on the same real data.
     */
    /** Photos the models can read, plus PDF and plain-text files. */
    private static final Set<String> FILE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp", "image/gif", "application/pdf", "text/plain", "text/csv");
    private static final int MAX_FILE_BYTES = 5 * 1024 * 1024;

    public String chat(User me, String message) {
        return chat(me, message, List.of());
    }

    /**
     * With an AI key: the agent (AgentService) — reads payroll, PayPal transactions, risk alerts, attached
     * photos and files, and can pay salaries through PayPal Payouts after the user confirms.
     * Without a key or if the AI call fails: rule-based answers on the same real data.
     */
    public String chat(User me, String message, List<Requests.Attachment> attachments) {
        List<LlmClient.Attachment> files = checkFiles(attachments);
        String q = message == null ? "" : message.trim();
        if (q.isEmpty() && files.isEmpty()) throw Errors.badRequest("Type a question or attach a file");
        if (q.isEmpty()) q = "Look at the attached file" + (files.size() > 1 ? "s" : "") + " and tell me what matters for us.";

        boolean acc = me.getRole() == Role.ACCOUNTANT;
        String reply;
        String footer = "";
        Optional<LlmClient.AgentResult> ai = agent.enabled() ? agent.answer(me, q, files) : Optional.empty();
        if (ai.isPresent()) {
            reply = plain(ai.get().text());
            if (!ai.get().toolsUsed().isEmpty()) {
                footer = "\n\n🔧 " + String.join(" → ", ai.get().toolsUsed());
            }
        } else if (!files.isEmpty()) {
            reply = "I can read photos and files only when the AI model is connected. "
                    + (agent.enabled() ? "It is busy right now — try again in a minute." : "Ask me in text meanwhile.");
        } else {
            reply = acc ? accountantRules(me, q) : employeeRules(me, q);
            // the key is set but the model did not answer: say so instead of pretending to be the AI
            if (agent.enabled()) footer = "\n\n⚠️ The AI model is busy right now — this is a quick answer from your data. Try again in a minute.";
        }

        ChatMessage c = new ChatMessage();
        c.setUser(me);
        // files are not stored, only their names: the history stays small
        String stored = files.isEmpty() ? q
                : q + " [attached: " + String.join(", ", files.stream().map(LlmClient.Attachment::name).toList()) + "]";
        c.setQuestion(stored.length() > 2000 ? stored.substring(0, 2000) : stored);
        c.setReply(reply.length() > 8000 ? reply.substring(0, 8000) : reply);
        chats.save(c);
        return reply + footer;
    }

    /** Only known types, valid base64, max 5 MB each (checked on the server, the browser can lie). */
    private static List<LlmClient.Attachment> checkFiles(List<Requests.Attachment> attachments) {
        if (attachments == null || attachments.isEmpty()) return List.of();
        if (attachments.size() > 3) throw Errors.badRequest("At most 3 files per message");
        List<LlmClient.Attachment> out = new ArrayList<>();
        for (Requests.Attachment a : attachments) {
            String type = a.type().toLowerCase(Locale.ROOT).trim();
            if (type.equals("image/jpg")) type = "image/jpeg";
            if (!FILE_TYPES.contains(type)) {
                throw Errors.badRequest(a.name() + ": unsupported file type. Use a photo, PDF, CSV or TXT.");
            }
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(a.data());
            } catch (IllegalArgumentException e) {
                throw Errors.badRequest(a.name() + ": broken file data");
            }
            if (bytes.length > MAX_FILE_BYTES) throw Errors.badRequest(a.name() + ": file is too large (max 5 MB)");
            out.add(new LlmClient.Attachment(a.name(), type, a.data()));
        }
        return out;
    }

    /** The chat shows plain text: drop markdown the model may still use (**bold**, # headings). */
    static String plain(String text) {
        return text.replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("(?m)^#{1,6}\\s+", "")
                .replaceAll("(?m)^\\s*\\*\\s+", "- ");
    }

    public List<String> suggestions(User me) {
        return me.getRole() == Role.ACCOUNTANT
                ? List.of("Show pending approvals", "How much do I owe in salaries this month?",
                "Which payments look unusual?", "Summarise cash flow for this month", "How much did Mass Pay save us?")
                : List.of("When is my next payday?", "Show my last PayPal payouts",
                "How many hours did I log this week?", "How do I withdraw to my bank?");
    }

    /** The user's latest unique questions. */
    @Transactional(readOnly = true)
    public List<String> history(User me) {
        LinkedHashSet<String> uniq = new LinkedHashSet<>();
        for (ChatMessage c : chats.findTop20ByUserOrderByCreatedAtDesc(me)) {
            String q = c.getQuestion().trim();
            uniq.add(q.length() > 60 ? q.substring(0, 57) + "..." : q);
            if (uniq.size() == 5) break;
        }
        return new ArrayList<>(uniq);
    }

    /* ======================= AI summary ======================= */

    @Transactional(readOnly = true)
    public AiSummary summary(User me) {
        String updated = "Today, " + Fmt.hm(LocalDateTime.now());
        List<SummaryPoint> pts = new ArrayList<>();
        YearMonth m = me.getRole() == Role.ACCOUNTANT ? payroll.payPeriod(me.getCompany()) : payroll.payPeriod(me);

        if (me.getRole() == Role.ACCOUNTANT) {
            var month = payroll.companyMonth(me.getCompany(), m);
            var unpaid = month.stream().filter(e -> !e.paid()).toList();
            var paid = month.stream().filter(PayrollService.EmployeeMonth::paid).map(e -> e.user().getName()).toList();
            if (!unpaid.isEmpty()) {
                pts.add(new SummaryPoint("warn", "Payroll due this month is $" + money(PayrollService.total(unpaid))
                        + " for " + unpaid.size() + " unpaid employees."
                        + (paid.isEmpty() ? "" : " Already paid: " + String.join(", ", paid) + ".")));
            } else if (!month.isEmpty()) {
                pts.add(new SummaryPoint("good", "Everyone is paid for " + Fmt.monthLong(m) + "."));
            }
            month.stream().max(Comparator.comparingInt(PayrollService.EmployeeMonth::tasksDone))
                    .filter(e -> e.tasksDone() > 0)
                    .ifPresent(e -> pts.add(new SummaryPoint("good", e.user().getName() + " closed the most tasks ("
                            + e.tasksDone() + " of " + e.tasksTotal() + ") and " + e.projectsEnded() + " projects.")));
            month.stream().filter(e -> e.tasksTotal() >= 3)
                    .min(Comparator.comparingDouble(e -> (double) e.tasksDone() / e.tasksTotal()))
                    .filter(e -> (double) e.tasksDone() / e.tasksTotal() < 0.6)
                    .ifPresent(e -> pts.add(new SummaryPoint("warn", e.user().getName() + " finished "
                            + e.tasksDone() + " of " + e.tasksTotal() + " tasks. Check workload before approving overtime.")));
            BigDecimal unpaidSum = PayrollService.total(unpaid);
            if (unpaidSum.signum() > 0) {
                BigDecimal save = unpaidSum.multiply(PayrollService.SINGLE_PAYOUT_FEE_RATE
                        .subtract(PayrollService.MASS_PAY_FEE_RATE));
                pts.add(new SummaryPoint("good", "Paying everyone in one PayPal Mass Pay batch saves about $"
                        + money(save) + " compared with single payouts."));
            }
            int flagged = anomalies.detect(me.getCompany()).size();
            if (flagged > 0) {
                pts.add(new SummaryPoint("", flagged + " flagged transactions are still open. Review them before the payroll run on "
                        + Fmt.date(PayrollService.payday(m)) + "."));
            }
        } else {
            double thisWeek = weekHours(me, LocalDate.now());
            double lastWeek = weekHours(me, LocalDate.now().minusWeeks(1));
            String trend = lastWeek > 0 ? String.format(Locale.ENGLISH, ", %+.0f%% vs last week",
                    (thisWeek - lastWeek) / lastWeek * 100) : "";
            pts.add(new SummaryPoint(thisWeek >= lastWeek ? "good" : "warn",
                    "You logged " + Fmt.round1(thisWeek) + " hours this week" + trend + "."));
            LocalDate today = LocalDate.now();
            tasks.findByAssigneeOrderByDueDateAsc(me).stream()
                    .filter(t -> t.getStatus() != TaskStatus.DONE && t.getDueDate() != null && !t.getDueDate().isAfter(today.plusDays(2)))
                    .limit(2)
                    .forEach(t -> pts.add(new SummaryPoint("warn", "\"" + t.getTitle() + "\" is due on "
                            + Fmt.date(t.getDueDate()) + " and is still " + (t.getStatus() == TaskStatus.TODO ? "to do" : "in progress") + ".")));
            var cur = payroll.monthFor(me, m);
            pts.add(new SummaryPoint("good", cur.paid()
                    ? Fmt.monthLong(m) + " salary of $" + money(cur.salary()) + " is already paid."
                    : "Earnings so far this month: $" + money(cur.salary()) + ", payday is " + Fmt.date(PayrollService.payday(m)) + "."));
            if (cur.projectsEnded() > 0) {
                pts.add(new SummaryPoint("", cur.projectsEnded() + " of your projects finished this month (+$"
                        + money(cur.bonus()) + " bonus)."));
            }
        }
        return new AiSummary(updated, pts);
    }

    /* ======================= rule-based answers (no AI key) ======================= */

    private String accountantRules(User me, String q) {
        String t = q.toLowerCase();
        YearMonth m = payroll.payPeriod(me.getCompany());
        if (has(t, "pending", "approv", "одобр", "ожида", "zatwierdz", "oczekuj")) {
            List<PendingRow> p = accountantDashboard.pendingRows(me.getCompany());
            if (p.isEmpty()) return "No payments are waiting for approval right now.";
            PendingRow big = p.stream().max(Comparator.comparing(PendingRow::amount)).get();
            BigDecimal sum = p.stream().map(PendingRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            return "There are " + p.size() + " payments waiting for approval, $" + money(sum) + " in total. The largest is "
                    + big.name() + " ($" + money(big.amount()) + ", due " + big.due() + ").";
        }
        if (has(t, "salar", "payroll", "pay ", "employee", "staff", "worker", "зарплат", "зп", "owe", "выплат", "заплат", "работник", "сотрудник", "персонал", "wypłat", "wynagrodz", "pensj", "pracown")) {
            var month = payroll.companyMonth(me.getCompany(), m);
            var unpaid = month.stream().filter(e -> !e.paid()).toList();
            var paid = month.stream().filter(PayrollService.EmployeeMonth::paid).map(e -> e.user().getName()).toList();
            if (unpaid.isEmpty()) return "All salaries for " + Fmt.monthLong(m) + " are paid.";
            return "Unpaid salaries for " + Fmt.monthLong(m) + ": $" + money(PayrollService.total(unpaid)) + " for "
                    + unpaid.size() + " employees." + (paid.isEmpty() ? "" : " Already paid: " + String.join(", ", paid) + ".")
                    + " You can send all of it in one Mass Pay batch from the Tasks page.";
        }
        if (has(t, "unusual", "anomal", "risk", "suspic", "fraud", "аном", "риск", "подозр", "странн", "podejrz", "ryzyk")) {
            List<AnomalyRow> a = anomalies.detect(me.getCompany());
            if (a.isEmpty()) return "Nothing looks unusual in recent payments.";
            StringBuilder sb = new StringBuilder(a.size() + " payments are flagged: ");
            for (int i = 0; i < a.size(); i++) {
                if (i > 0) sb.append("; ");
                sb.append(a.get(i).detail()).append(" ($").append(money(a.get(i).amount())).append(")");
            }
            return sb.toString();
        }
        if (has(t, "cash", "flow", "income", "expense", "поток", "приход", "расход", "доход", "przepływ", "wydatk", "przychod")) {
            YearMonth cfMonth = YearMonth.now();
            var cf = accountantDashboard.cashFlow(txs.findByCompanyAndOwnerIsNullOrderByDateTimeDesc(me.getCompany()), cfMonth);
            double in = cf.bars().stream().mapToDouble(b -> b.in()).sum();
            double out = cf.bars().stream().mapToDouble(b -> b.out()).sum();
            return Fmt.monthLong(cfMonth) + " so far: inflow $" + Fmt.round1(in) + "k, outflow $" + Fmt.round1(out) + "k, net $"
                    + Fmt.round1(in - out) + "k. Forecast for next week: $" + cf.forecast().get(cf.forecast().size() - 1) + "k inflow.";
        }
        if (has(t, "save", "saving", "mass", "fee", "эконом", "комисс", "oszczęd", "prowizj")) {
            List<SavingRow> s = savings(me);
            BigDecimal total = s.stream().map(SavingRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            return "Mass Pay and Smart FX routing saved $" + money(total) + " this month ("
                    + s.get(0).label() + ": $" + money(s.get(0).amount()) + ", " + s.get(1).label() + ": $"
                    + money(s.get(1).amount()) + ").";
        }
        if (has(t, "balance", "баланс", "сколько денег", "счёт", "счет", "saldo", "stan konta")) {
            return accounts.findByCompany(me.getCompany())
                    .map(a -> "PayPal business balance: $" + money(a.getBalance()) + ".")
                    .orElse("The company PayPal account is not set up yet.");
        }
        return "I can help with payroll, pending approvals, unusual payments, cash flow and savings. Try one of the suggestions below.";
    }

    private String employeeRules(User me, String q) {
        String t = q.toLowerCase();
        YearMonth m = payroll.payPeriod(me);
        if (has(t, "payday", "salary", "earn", "зарплат", "зп", "когда", "заработ", "wypłat", "zarob", "pensj", "kiedy")) {
            var cur = payroll.monthFor(me, m);
            if (cur.paid()) return "Your " + Fmt.monthLong(m) + " salary ($" + money(cur.salary()) + ") is already on your PayPal account.";
            return "Your next salary is scheduled for " + Fmt.date(PayrollService.payday(m)) + ". So far this month you earned $"
                    + money(cur.salary()) + " (" + cur.hours() + " h × $" + money(me.getHourlyRate())
                    + (cur.bonus().signum() > 0 ? " + $" + money(cur.bonus()) + " project bonus" : "") + ").";
        }
        if (has(t, "payout", "paypal", "last", "выплат", "перевод", "przelew")) {
            List<Payout> list = payouts.findByUserOrderByDateDesc(me).stream().limit(3).toList();
            if (list.isEmpty()) return "You have no payouts yet.";
            StringBuilder sb = new StringBuilder("Last payouts: ");
            for (int i = 0; i < list.size(); i++) {
                Payout p = list.get(i);
                if (i > 0) sb.append(", ");
                sb.append('$').append(money(p.getAmount())).append(" on ").append(Fmt.date(p.getDate()))
                        .append(" (").append(p.getLabel()).append(')');
            }
            return sb.append('.').toString();
        }
        if (has(t, "hour", "time", "час", "время", "godzin", "czas")) {
            LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            double best = 0;
            LocalDate bestDay = monday;
            for (LocalDate d = monday; !d.isAfter(LocalDate.now()); d = d.plusDays(1)) {
                double h = payroll.secondsOn(me, d) / 3600.0;
                if (h > best) {
                    best = h;
                    bestDay = d;
                }
            }
            return "This week you logged " + Fmt.round1(weekHours(me, LocalDate.now())) + " hours."
                    + (best > 0 ? " " + bestDay.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                    + " was your longest day with " + Fmt.round1(best) + " hours." : "");
        }
        if (has(t, "withdraw", "bank", "вывод", "вывест", "банк", "konto bankowe")) {
            return "Open My wallet, press Withdraw to PayPal, enter the amount and confirm. You have $"
                    + money(me.getWalletBalance()) + " available. Transfers to your bank usually take 1 to 3 business days.";
        }
        if (has(t, "task", "задач", "zadan")) {
            List<Task> open = tasks.findByAssigneeOrderByDueDateAsc(me).stream()
                    .filter(x -> x.getStatus() != TaskStatus.DONE).toList();
            if (open.isEmpty()) return "You have no open tasks. Nice!";
            return "You have " + open.size() + " open tasks. Next due: \"" + open.get(0).getTitle() + "\" on "
                    + Fmt.date(open.get(0).getDueDate()) + ".";
        }
        return "I can help with your payouts, payday, hours and tasks. Try one of the suggestions below.";
    }

    /* ======================= helpers ======================= */

    private List<SavingRow> savings(User me) {
        return accountantDashboard.savings(txs.findByCompanyAndOwnerIsNullOrderByDateTimeDesc(me.getCompany()), YearMonth.now());
    }

    private double weekHours(User me, LocalDate anyDay) {
        LocalDate monday = anyDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return payroll.logs(me, monday.atStartOfDay(), monday.plusWeeks(1).atStartOfDay()).stream()
                .mapToLong(TimeLog::getSeconds).sum() / 3600.0;
    }

    private static boolean has(String text, String... words) {
        for (String w : words) if (text.contains(w)) return true;
        return false;
    }

    private static String money(BigDecimal v) {
        return String.format(Locale.ENGLISH, "%,.2f", Fmt.money(v));
    }
}
