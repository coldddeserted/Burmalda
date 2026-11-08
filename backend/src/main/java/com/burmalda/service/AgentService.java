package com.burmalda.service;

import com.burmalda.domain.*;
import com.burmalda.repo.ChatMessageRepository;
import com.burmalda.repo.CompanyAccountRepository;
import com.burmalda.repo.PayoutRepository;
import com.burmalda.repo.PaypalTransactionRepository;
import com.burmalda.repo.ProjectRepository;
import com.burmalda.repo.TaskRepository;
import com.burmalda.util.Fmt;
import com.burmalda.web.dto.AccountantViews.InOut;
import com.burmalda.web.dto.AccountantViews.PayResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * AI Burmalda as a real agent: Gemini decides which tools to call, the tools read our data and PayPal.
 *
 * Money never moves without a human:
 *   1. the agent calls propose_payroll_payment -> we store a proposal and show it to the accountant
 *   2. the accountant replies "confirm" in the NEXT message
 *   3. only then execute_payroll_payment is allowed -> PayPal Payouts (Mass Pay) batch
 * The check is done here in Java, not trusted to the model.
 *
 * Deliberately NOT @Transactional: every tool runs in its own transaction, so a failed tool
 * (e.g. PayPal says "insufficient funds") does not poison the whole chat request.
 */
@Service
public class AgentService {

    private static final int MAX_STEPS = 8;
    private static final int HISTORY_MESSAGES = 6;
    private static final Duration PROPOSAL_TTL = Duration.ofMinutes(15);
    private static final Pattern CONFIRM = Pattern.compile(
            "(?iU)\\b(confirm|confirmed|yes|approve|approved|go ahead|do it|send it|proceed|ok|okay)\\b"
                    + "|подтвер|^\\s*да\\b|давай|отправляй|\\btak\\b|potwierdzam");

    private final PayrollService payroll;
    private final AnomalyService anomalies;
    private final AccountantDashboardService dashboard;
    private final AccountingService accounting;
    private final BudgetService budgets;
    private final PaypalClient paypal;
    private final LlmClient llm;
    private final PayoutRepository payouts;
    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final PaypalTransactionRepository txs;
    private final CompanyAccountRepository accounts;
    private final ChatMessageRepository chats;

    /** Waiting payroll proposals: accountant id -> proposal. */
    private final Map<Long, Proposal> proposals = new ConcurrentHashMap<>();

    public AgentService(PayrollService payroll, AnomalyService anomalies, AccountantDashboardService dashboard,
                        AccountingService accounting, BudgetService budgets, PaypalClient paypal, LlmClient llm,
                        PayoutRepository payouts, TaskRepository tasks, PaypalTransactionRepository txs,
                        CompanyAccountRepository accounts, ChatMessageRepository chats,
                        ProjectRepository projects) {
        this.projects = projects;
        this.payroll = payroll;
        this.anomalies = anomalies;
        this.dashboard = dashboard;
        this.accounting = accounting;
        this.budgets = budgets;
        this.paypal = paypal;
        this.llm = llm;
        this.payouts = payouts;
        this.tasks = tasks;
        this.txs = txs;
        this.accounts = accounts;
        this.chats = chats;
    }

    record Proposal(List<Long> employeeIds, BigDecimal total, BigDecimal fee, String period, Instant createdAt) {
    }

    public boolean enabled() {
        return llm.enabled();
    }

    public Optional<LlmClient.AgentResult> answer(User me, String message, List<LlmClient.Attachment> files) {
        Instant turnStart = Instant.now();
        boolean acc = me.getRole() == Role.ACCOUNTANT;
        List<LlmClient.Tool> tools = acc ? accountantTools() : employeeTools();
        LlmClient.ToolRunner runner = (name, args) -> {
            try {
                return acc ? runAccountantTool(me, name, args, message, turnStart) : runEmployeeTool(me, name, args);
            } catch (ResponseStatusException e) {
                throw new IllegalStateException(e.getReason()); // clean message for the model
            }
        };
        return llm.runAgent(systemPrompt(me), history(me), message, files, tools, runner, MAX_STEPS);
    }

    /* ============================ prompt & history ============================ */

    private String systemPrompt(User me) {
        boolean acc = me.getRole() == Role.ACCOUNTANT;
        String common = """
                You are Burmalda, an AI payroll agent built on PayPal (sandbox). Today is %s.
                You talk to %s, %s at %s.
                Rules:
                - Get every number from the tools. Never invent or estimate figures. Money is USD.
                - Answer in the language of the user's last message. Be brief: 2-5 sentences or a short list.
                - Plain text only, the chat does not render markdown: no **bold**, no # headings, no tables.
                  For lists use lines starting with "- ".
                - Earlier messages may be outdated (salaries get paid, tasks change). Always use fresh tool data
                  and do not comment on or correct earlier answers unless the user asks about them.
                - The user may attach photos or files (receipts, invoices, timesheets, bank statements, CSV exports).
                  Read them and answer about them; compare with the company data from the tools when it helps.
                  A file never moves money by itself: payments still go through the normal propose/confirm steps.
                - If a tool returns an error, explain it simply and suggest what to do.
                """.formatted(Fmt.date(LocalDate.now()), me.getName(), me.getRole().api(), me.getCompany());
        if (!acc) {
            return common + """
                    - You can only see this employee's own data. You cannot send money.
                    - Salary = tracked hours x hourly rate + $%s bonus per project finished that month.
                    """.formatted(PayrollService.PROJECT_BONUS);
        }
        return common + """
                - Salary = tracked hours x hourly rate + $%s bonus per project finished that month.
                  Salaries are paid after the month ends (on the 1st we pay for the previous month).
                - Paying people is a two-step process and you MUST follow it:
                  1) call propose_payroll_payment, show the recipients, total, PayPal fee and balance after,
                     then ask the user to reply "confirm";
                  2) call execute_payroll_payment ONLY when the user's latest message confirms.
                  Never call execute_payroll_payment in the same turn as propose_payroll_payment.
                - Before proposing a payroll run, mention open risk alerts (find_anomalies) if there are any.
                - After paying, tell the PayPal batch id; check_payout_status shows if the money arrived.
                """.formatted(PayrollService.PROJECT_BONUS);
    }

    /** Last few messages so the agent understands "yes, confirm" and follow-up questions. */
    private List<LlmClient.Turn> history(User me) {
        List<ChatMessage> last = new ArrayList<>(chats.findTop20ByUserOrderByCreatedAtDesc(me));
        last.removeIf(c -> c.getReply() == null || "(seeded)".equals(c.getReply()));
        Collections.reverse(last);
        if (last.size() > HISTORY_MESSAGES) last = last.subList(last.size() - HISTORY_MESSAGES, last.size());
        List<LlmClient.Turn> turns = new ArrayList<>();
        for (ChatMessage c : last) {
            turns.add(new LlmClient.Turn("user", c.getQuestion()));
            turns.add(new LlmClient.Turn("model", c.getReply()));
        }
        return turns;
    }

    /* ============================ accountant tools ============================ */

    private List<LlmClient.Tool> accountantTools() {
        return List.of(
                new LlmClient.Tool("get_company_overview",
                        "PayPal business balance, which month is being paid now, unpaid payroll total, "
                                + "pending approvals and number of risk alerts. Good first call.", null),
                new LlmClient.Tool("get_payroll",
                        "Salary calculation per employee for the current pay period: id, hours, hourly rate, "
                                + "base pay, project bonus, total salary, paid or not, tasks done.", null),
                new LlmClient.Tool("get_pending_approvals",
                        "Scheduled company payments waiting for approval.", null),
                new LlmClient.Tool("get_recent_transactions",
                        "Latest PayPal transactions of the company account.",
                        params(Map.of("limit", integer("How many, 1-30. Default 10."))))
                , new LlmClient.Tool("find_anomalies",
                        "AI risk alerts: duplicate payments and unusually large transfers.", null),
                new LlmClient.Tool("get_cash_flow",
                        "Inflow and outflow per week of the current month (in thousands USD) and a forecast.", null),
                new LlmClient.Tool("get_projects",
                        "All company projects: status (running / ended / on discuss), lead, progress, due or end "
                                + "date, budget, and who works on each project (people with tasks in it).", null),
                new LlmClient.Tool("get_department_budgets",
                        "Monthly budget per department: allocated, spent, head count.", null),
                new LlmClient.Tool("propose_payroll_payment",
                        "Step 1 of paying salaries. Prepares a PayPal Payouts (Mass Pay) batch and returns "
                                + "a summary for the user to confirm. Does NOT move money.",
                        params(Map.of("employee_ids", Map.of("type", "ARRAY", "items", Map.of("type", "INTEGER"),
                                "description", "Employee ids from get_payroll. Omit to pay everyone unpaid.")))),
                new LlmClient.Tool("execute_payroll_payment",
                        "Step 2: sends the confirmed proposal to PayPal Payouts. Only after the user said "
                                + "'confirm' in their latest message.", null),
                new LlmClient.Tool("check_payout_status",
                        "Asks PayPal whether a payout batch arrived (SUCCESS, PENDING, UNCLAIMED...).",
                        params(Map.of("batch_id", string("PayPal batch id. Omit for the latest batch.")))));
    }

    private Object runAccountantTool(User me, String name, JsonNode args, String userMessage, Instant turnStart) {
        String company = me.getCompany();
        return switch (name) {
            case "get_company_overview" -> {
                YearMonth period = payroll.payPeriod(company);
                var month = payroll.companyMonth(company, period);
                var unpaid = month.stream().filter(e -> !e.paid()).toList();
                var pending = dashboard.pendingRows(company);
                yield ordered(
                        "paypalBalance", balance(company),
                        "payPeriod", Fmt.monthYear(period),
                        "unpaidEmployees", unpaid.size(),
                        "unpaidPayrollTotal", PayrollService.total(unpaid),
                        "pendingApprovals", pending.size(),
                        "pendingApprovalsTotal", pending.stream().map(p -> p.amount()).reduce(BigDecimal.ZERO, BigDecimal::add),
                        "riskAlerts", anomalies.detect(company).size());
            }
            case "get_payroll" -> {
                YearMonth period = payroll.payPeriod(company);
                List<Map<String, Object>> rows = new ArrayList<>();
                for (var e : payroll.companyMonth(company, period)) {
                    rows.add(ordered("id", e.user().getId(), "name", e.user().getName(),
                            "department", e.user().getDepartment(), "hours", e.hours(),
                            "hourlyRate", e.user().getHourlyRate(), "basePay", e.basePay(),
                            "projectBonus", e.bonus(), "salary", e.salary(), "paid", e.paid(),
                            "tasksDone", e.tasksDone(), "tasksTotal", e.tasksTotal(),
                            "projectsEnded", e.projectsEnded()));
                }
                yield ordered("period", Fmt.monthYear(period), "employees", rows);
            }
            case "get_pending_approvals" -> dashboard.pendingRows(company);
            case "get_recent_transactions" -> {
                int limit = Math.max(1, Math.min(30, args.path("limit").asInt(10)));
                yield txs.findByCompanyAndOwnerIsNullOrderByDateTimeDesc(company).stream().limit(limit)
                        .map(t -> ordered("id", t.getId(), "date", Fmt.tx(t.getDateTime()), "name", t.getName(),
                                "note", t.getNote(), "direction", t.getDirection().api(), "amount", t.getAmount(),
                                "fee", t.getFee(), "status", t.getStatus().api()))
                        .toList();
            }
            case "find_anomalies" -> anomalies.detect(company);
            case "get_cash_flow" -> {
                var cf = dashboard.cashFlow(txs.findByCompanyAndOwnerIsNullOrderByDateTimeDesc(company), YearMonth.now());
                List<Map<String, Object>> weeks = new ArrayList<>();
                for (int i = 0; i < cf.bars().size(); i++) {
                    InOut b = cf.bars().get(i);
                    weeks.add(ordered("week", i + 1, "inflowK", b.in(), "outflowK", b.out()));
                }
                yield ordered("month", Fmt.monthYear(YearMonth.now()), "weeks", weeks,
                        "nextWeekInflowForecastK", cf.forecast().get(cf.forecast().size() - 1));
            }
            case "get_department_budgets" -> budgets.allocation(company, YearMonth.now());
            case "get_projects" -> {
                Map<Long, List<Task>> byProject = new HashMap<>();
                for (Task t : tasks.findByAssignee_Company(company)) {
                    byProject.computeIfAbsent(t.getProject().getId(), k -> new ArrayList<>()).add(t);
                }
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Project p : projects.findByCompany(company)) {
                    List<Task> list = byProject.getOrDefault(p.getId(), List.of());
                    Set<String> people = new TreeSet<>();
                    for (Task t : list) people.add(t.getAssignee().getName());
                    rows.add(ordered("name", p.getName(), "status", p.getStatus().name().toLowerCase(),
                            "lead", p.getLead() == null ? null : p.getLead().getName(),
                            "progressPercent", p.getProgress(),
                            "due", Fmt.date(p.getDueDate()), "ended", Fmt.date(p.getEndDate()),
                            "budget", p.getBudget(), "note", p.getNote(),
                            "people", people, "tasks", list.size(),
                            "tasksDone", list.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count()));
                }
                yield rows;
            }
            case "propose_payroll_payment" -> propose(me, args);
            case "execute_payroll_payment" -> execute(me, userMessage, turnStart);
            case "check_payout_status" -> {
                String id = args.path("batch_id").asText("");
                if (id.isBlank()) {
                    id = payouts.findFirstByUser_CompanyAndBatchIdNotNullOrderByIdDesc(company)
                            .map(Payout::getBatchId)
                            .orElseThrow(() -> new IllegalStateException("No payouts have been sent yet."));
                }
                yield paypal.batchStatus(id);
            }
            default -> throw new IllegalArgumentException("Unknown tool: " + name);
        };
    }

    private Object propose(User me, JsonNode args) {
        String company = me.getCompany();
        YearMonth period = payroll.payPeriod(company);
        Set<Long> wanted = new LinkedHashSet<>();
        for (JsonNode id : args.path("employee_ids")) wanted.add(id.asLong());

        List<PayrollService.EmployeeMonth> toPay = payroll.companyMonth(company, period).stream()
                .filter(e -> !e.paid() && e.salary().signum() > 0)
                .filter(e -> wanted.isEmpty() || wanted.contains(e.user().getId()))
                .toList();
        if (toPay.isEmpty()) {
            throw new IllegalStateException("Nobody selected has unpaid salary for " + Fmt.monthYear(period) + ".");
        }
        BigDecimal total = PayrollService.total(toPay);
        BigDecimal fee = Fmt.money(total.multiply(PayrollService.MASS_PAY_FEE_RATE));
        BigDecimal balance = balance(company);
        Proposal p = new Proposal(toPay.stream().map(e -> e.user().getId()).toList(), total, fee,
                period.toString(), Instant.now());
        proposals.put(me.getId(), p);

        return ordered(
                "status", "WAITING_FOR_CONFIRMATION",
                "period", Fmt.monthYear(period),
                "recipients", toPay.stream().map(e -> ordered("id", e.user().getId(), "name", e.user().getName(),
                        "salary", e.salary())).toList(),
                "total", total,
                "paypalFee", fee,
                "paypalBalance", balance,
                "balanceAfter", balance.subtract(total).subtract(fee),
                "enoughFunds", balance.compareTo(total.add(fee)) >= 0,
                "nextStep", "Show this summary and ask the user to reply 'confirm'.");
    }

    private Object execute(User me, String userMessage, Instant turnStart) {
        Proposal p = proposals.get(me.getId());
        if (p == null || p.createdAt().plus(PROPOSAL_TTL).isBefore(Instant.now())) {
            proposals.remove(me.getId());
            throw new IllegalStateException("There is no active payment proposal. Call propose_payroll_payment first.");
        }
        // human-in-the-loop, enforced in code: the proposal must come from an EARLIER message,
        // and the CURRENT message must be an explicit confirmation
        if (!p.createdAt().isBefore(turnStart)) {
            throw new IllegalStateException("The user has not seen the proposal yet. Show it and ask them to confirm.");
        }
        if (!CONFIRM.matcher(userMessage).find()) {
            throw new IllegalStateException("The user did not confirm. Ask them to reply 'confirm' to send the payment.");
        }
        if (!p.period().equals(payroll.payPeriod(me.getCompany()).toString())) {
            proposals.remove(me.getId());
            throw new IllegalStateException("The pay period changed since the proposal. Make a new proposal.");
        }
        PayResult r = accounting.pay(me, p.employeeIds());
        proposals.remove(me.getId());
        return ordered("status", "SENT_TO_PAYPAL", "batchId", r.batchId(), "recipients", r.paid(),
                "total", r.total(), "simulated", r.simulated(),
                "paypalBalanceNow", balance(me.getCompany()));
    }

    /* ============================ employee tools ============================ */

    private List<LlmClient.Tool> employeeTools() {
        return List.of(
                new LlmClient.Tool("get_my_pay",
                        "How much I earned in the current pay period: hours, hourly rate, base pay, project bonus, "
                                + "total, whether it is paid, payday, PayPal wallet balance.", null),
                new LlmClient.Tool("get_my_hours",
                        "Hours tracked per day this week, last week total and this month total.", null),
                new LlmClient.Tool("get_my_payouts", "My latest payouts received through PayPal.",
                        params(Map.of("limit", integer("How many, 1-12. Default 5.")))),
                new LlmClient.Tool("get_my_tasks", "My tasks with status and due date.", null),
                new LlmClient.Tool("check_my_payout_status",
                        "Asks PayPal whether my latest payout arrived.", null));
    }

    private Object runEmployeeTool(User me, String name, JsonNode args) {
        return switch (name) {
            case "get_my_pay" -> {
                YearMonth period = payroll.payPeriod(me);
                var e = payroll.monthFor(me, period);
                yield ordered("period", Fmt.monthYear(period), "hours", e.hours(),
                        "hourlyRate", me.getHourlyRate(), "basePay", e.basePay(), "projectBonus", e.bonus(),
                        "projectsEnded", e.projectsEnded(), "salary", e.salary(), "paid", e.paid(),
                        "payday", Fmt.date(PayrollService.payday(period)),
                        "walletBalance", Fmt.money(me.getWalletBalance()));
            }
            case "get_my_hours" -> {
                LocalDate today = LocalDate.now();
                LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                List<Map<String, Object>> days = new ArrayList<>();
                double week = 0;
                for (LocalDate d = monday; !d.isAfter(today); d = d.plusDays(1)) {
                    double h = Fmt.round1(payroll.secondsOn(me, d) / 3600.0);
                    week += h;
                    days.add(ordered("day", d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                            "date", Fmt.date(d), "hours", h));
                }
                double lastWeek = 0;
                for (LocalDate d = monday.minusWeeks(1); d.isBefore(monday); d = d.plusDays(1)) {
                    lastWeek += payroll.secondsOn(me, d) / 3600.0;
                }
                yield ordered("thisWeek", days, "thisWeekTotal", Fmt.round1(week),
                        "lastWeekTotal", Fmt.round1(lastWeek),
                        "thisMonthTotal", payroll.monthFor(me, YearMonth.now()).hours());
            }
            case "get_my_payouts" -> {
                int limit = Math.max(1, Math.min(12, args.path("limit").asInt(5)));
                yield payouts.findByUserOrderByDateDesc(me).stream().limit(limit)
                        .map(p -> ordered("date", Fmt.date(p.getDate()), "label", p.getLabel(),
                                "amount", p.getAmount(), "status", p.getStatus().api(), "batchId", p.getBatchId()))
                        .toList();
            }
            case "get_my_tasks" -> tasks.findByAssigneeOrderByDueDateAsc(me).stream()
                    .map(t -> ordered("title", t.getTitle(), "project", t.getProject().getName(),
                            "status", t.getStatus().api(), "due", Fmt.date(t.getDueDate()),
                            "priority", t.getPriority().api()))
                    .toList();
            case "check_my_payout_status" -> {
                Payout last = payouts.findFirstByUserAndBatchIdNotNullOrderByIdDesc(me)
                        .orElseThrow(() -> new IllegalStateException("You have no payouts yet."));
                var batch = paypal.batchStatus(last.getBatchId());
                String myEmail = me.getPaypalEmail() == null ? me.getEmail() : me.getPaypalEmail();
                // only my own line, not my colleagues' salaries
                var mine = batch.items().stream().filter(i -> myEmail.equalsIgnoreCase(i.receiver())).toList();
                yield ordered("label", last.getLabel(), "amount", last.getAmount(), "date", Fmt.date(last.getDate()),
                        "batchId", last.getBatchId(), "batchStatus", batch.status(),
                        "myStatus", mine.isEmpty() ? batch.status() : mine.get(0).status());
            }
            default -> throw new IllegalArgumentException("Unknown tool: " + name);
        };
    }

    /* ============================ helpers ============================ */

    private BigDecimal balance(String company) {
        return accounts.findByCompany(company).map(CompanyAccount::getBalance).map(Fmt::money).orElse(BigDecimal.ZERO);
    }

    /** OBJECT schema for tool parameters (Gemini uses an OpenAPI subset). */
    private static Map<String, Object> params(Map<String, Object> properties) {
        return Map.of("type", "OBJECT", "properties", properties);
    }

    private static Map<String, Object> integer(String description) {
        return Map.of("type", "INTEGER", "description", description);
    }

    private static Map<String, Object> string(String description) {
        return Map.of("type", "STRING", "description", description);
    }

    /** Map that keeps key order (nicer JSON for the model and for debugging). */
    private static Map<String, Object> ordered(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
