# Burmalda — backend

Spring Boot 3.5 · Java 21 · Maven · Spring Security + JWT · JPA/Hibernate · H2 (local) / PostgreSQL (prod) · PayPal Payouts API · Google Gemini

The server behind Burmalda: employees track time and tasks → salaries are calculated automatically → an AI agent prepares the payroll run → the accountant confirms → salaries are paid through **PayPal Payouts (Mass Pay)**.

## Quick start
1. IntelliJ IDEA → **File → Open** → this `backend` folder (Maven loads by itself). JDK 21+.
2. Run `BurmaldaApplication`.
3. Open **http://localhost:8080** — the frontend is served by the backend (`src/main/resources/static`).

Demo logins (password `demo1234` for all):

| Email | Role |
|---|---|
| `accountant@burmalda.com` | accountant (Anna Kowalska) |
| `engineer@burmalda.com` | employee (Edgar Wilson) |
| `leon@`, `alexandra@`, `jack@`, `maria@`, `tomasz@`, `sofia@`, `daniel@burmalda.com` | employees |

Useful:
- http://localhost:8080/api/health — server status and whether PayPal / AI are live
- http://localhost:8080/h2-console — database (JDBC URL `jdbc:h2:file:./data/burmalda`, user `sa`, empty password)
- Reset demo data: stop the server, delete the `data/` folder, start again. Demo dates are relative to today.

## Environment variables
All optional — without them the app runs in demo mode.

| Variable | What for | Without it |
|---|---|---|
| `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET` | real payouts in **PayPal Sandbox** (developer.paypal.com → Apps & Credentials → Sandbox; enable *Payouts* for the app) | simulated payouts, batch ids start with `SIM-` |
| `AI_API_KEY` | AI agent on **Google Gemini** (free key: aistudio.google.com → Get API key) | rule-based answers on the same real data |
| `AI_MODEL` | Gemini model id | `gemini-3.8-flash` |
| `AI_FALLBACK_MODELS` | comma-separated models to try when the main one is overloaded (503) or rate-limited (429); each model is retried 3 times first | none |
| `JWT_SECRET` | token signing secret, 32+ characters | dev secret — **set your own in production** |
| `SEED_DEMO` | `false` = no demo data | demo data is created |

In IDEA: Run → Edit Configurations → `BurmaldaApplication` → *Environment variables*, e.g.
`PAYPAL_CLIENT_ID=...;PAYPAL_CLIENT_SECRET=...;AI_API_KEY=...`

> The sandbox business account pays salaries in USD — create it as a **United States** business account with enough balance, otherwise PayPal answers `INSUFFICIENT_FUNDS`.

Production (PostgreSQL): profile `prod` + `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`.

## How it works

### Payroll
`PayrollService`:
```
salary for a month = tracked hours × hourly rate
                   + $150 for every project finished that month where the employee had tasks
```
Salaries are paid after the month ends: on November 1st the pay period is October (while anyone is still unpaid for it).
The amount is always calculated on the server — the frontend is never trusted with money.

### PayPal
- **Payouts API (Mass Pay)** — one batch pays many employees (`POST /v1/payments/payouts`), 1% fee vs ~2% for single transfers; the difference is shown as savings.
- **Payout status** — `GET /v1/payments/payouts/{batch_id}`: SUCCESS / PENDING / UNCLAIMED per employee.
- OAuth2 client credentials with token caching (`PaypalClient`).

### AI agent (`AgentService` + `LlmClient`)
Gemini with **function calling**: the model decides which tools to use, our code runs them on real data and PayPal.

Accountant tools: `get_company_overview`, `get_payroll`, `get_pending_approvals`, `get_recent_transactions`, `find_anomalies`, `get_cash_flow`, `get_department_budgets`, `propose_payroll_payment`, `execute_payroll_payment`, `check_payout_status`.
Employee tools: `get_my_pay`, `get_my_hours`, `get_my_payouts`, `get_my_tasks`, `check_my_payout_status` (only their own data).

**Human in the loop, enforced in code:** the agent can only *propose* a payroll run. Money moves only if the proposal was shown in an earlier message **and** the accountant's latest message is an explicit confirmation ("confirm", "yes", "approve"…). The model cannot skip this.

Example (illustrative numbers):
> **Accountant:** Pay everyone for October
> **Agent:** 7 employees, $21,363.00 + $213.63 PayPal fee, balance after $62,673.37. 2 risk alerts are open (duplicate Slack charge). Reply "confirm" to send.
> **Accountant:** confirm
> **Agent:** Sent via PayPal Payouts, batch `5UXD2E8A7EBQJ`. 🔧 execute_payroll_payment

**AI risk alerts** (`AnomalyService`): duplicate payments (same payee and amount within 3 days) and payments 2× above the payee's average.

## API
All paths start with `/api`. Auth: `Authorization: Bearer <token>`. Errors: `{"message": "..."}`.

| Method | Path | Who | What |
|---|---|---|---|
| POST | `/auth/login` | everyone | `{email,password}` → `{token,user}` |
| POST | `/auth/register` | everyone | `{name,email,password,role,department,company}` → `{user}` |
| POST | `/auth/logout` | logged in | revokes the token |
| GET | `/auth/me` | logged in | current user |
| GET | `/dashboard/employee` | employee | dashboard |
| GET | `/dashboard/accountant` | accountant | dashboard |
| GET | `/tasks/mine` · PATCH `/tasks/{id}` | logged in | tasks, `{status: todo/progress/done}` |
| POST | `/team/members` | logged in | `{name,email,task}` — give a colleague a task |
| POST | `/time/log` | employee | `{seconds}` — save time from the tracker |
| GET | `/wallet` · POST `/wallet/withdraw` | employee | wallet, withdraw `{amount}` |
| GET | `/accounting/team` | accountant | who worked how much and earned what |
| POST | `/accounting/pay` | accountant | `{ids}` → PayPal Mass Pay |
| GET | `/accounting/payouts/{batchId}/status` | accountant | PayPal payout status (`latest` = last batch) |
| GET | `/accounting/finance` | accountant | balance, reserves, upcoming payments, budgets |
| POST | `/accounting/funds` · `/accounting/payment` | accountant | add funds `{amount}` · payment `{to,amount,note}` |
| GET | `/accounting/approvals` · POST `/accounting/approvals/{id}/approve` | accountant | scheduled payments waiting for approval |
| GET | `/paypal/transactions` | logged in | accountant: company account, employee: own wallet |
| GET | `/reports` · POST `/reports/{id}/generate` | logged in | `{format,period: Month/Quarter/Year}` → CSV |
| POST | `/ai/chat` · GET `/ai/suggestions` `/ai/history` `/ai/summary` | logged in | AI Burmalda |
| GET | `/search?q=` | logged in | tasks, projects, people |

## Structure
```
com.burmalda
├── config/      SecurityConfig (roles, JWT), WebConfig (CORS), DataSeeder (demo data)
├── security/    JwtService (HS256 by hand, no libraries), JwtAuthFilter, AuthUser
├── domain/      entities: User, Project, Task, TimeLog, Payout, PaypalTransaction,
│                ScheduledPayment, DepartmentBudget, CompanyAccount, ComplianceItem, ChatMessage
├── repo/        Spring Data repositories
├── service/     business logic: PayrollService, PaypalClient, LlmClient, AgentService, AiService …
├── web/         controllers + ApiExceptionHandler
│   └── dto/     Requests (incoming), *Views (responses in the frontend's format)
└── util/        Fmt (date/money formats), Ids, Errors
```

## Known simplifications
- Tokens revoked by logout are kept in memory — after a restart they are valid again until they expire (12 h).
- Withdrawing from PayPal to a bank is only recorded in our database: the PayPal API has no such call.
- Reports are CSV only.
- Pending payroll proposals of the agent live in memory (15 minutes).
