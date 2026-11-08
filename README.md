# Burmalda

**AI-powered time tracking and payroll on PayPal.** Employees track their tasks and hours, Burmalda calculates every salary automatically, an AI agent prepares the monthly payroll run for the accountant, and after a human confirms, everyone is paid in one **PayPal Payouts (Mass Pay)** batch.

Built for the **"Build What's Next with PayPal and AI"** hackathon.

## The problem
In small companies the accountant spends the end of every month collecting hours from spreadsheets, checking who finished what, calculating salaries by hand and sending payments one by one. It is slow, error-prone and expensive (a fee for every single transfer).

## What Burmalda does
1. **Employees** track time with a built-in timer and move their tasks across a board.
2. **Burmalda calculates salaries** from real data: `tracked hours × hourly rate + a bonus for every project finished that month`.
3. **The AI agent** reviews the payroll, flags risky payments (duplicates, unusually large transfers) and proposes the payroll run: who gets how much, the PayPal fee and the balance afterwards.
4. **The accountant confirms** — only then the money moves.
5. **PayPal Payouts** pays all employees in one batch; the agent can check with PayPal whether every payout arrived.

## PayPal + AI
**PayPal (sandbox)**
- **Payouts API (Mass Pay)** — one batch pays many employees (`POST /v1/payments/payouts`), cheaper than single transfers; the saving is shown on the dashboard.
- **Payout status** — `GET /v1/payments/payouts/{batch_id}`: SUCCESS / PENDING / UNCLAIMED per employee.
- OAuth2 client credentials with token caching.

**AI — an agent with function calling on Anthropic Claude or Google Gemini**
The chat is a real agent: the model decides which tools to call, the backend runs them on live data and PayPal.
- Accountant tools: company overview, payroll calculation, pending approvals, recent PayPal transactions, risk alerts, cash flow, department budgets, **propose payroll payment**, **execute payroll payment**, **check payout status**.
- Employee tools: my pay, my hours, my payouts, my tasks, my payout status — only their own data.
- **Human in the loop, enforced in code:** the agent can only *propose* a payment. Money moves only if the proposal was shown in an earlier message and the accountant's latest message explicitly confirms it. The model cannot skip this step.
- Every answer shows which tools the agent used (`🔧 get_payroll → propose_payroll_payment`).
- **Voice, photos and files in the chat:** speak instead of typing (browser speech recognition), attach a photo of a receipt or an invoice PDF, a timesheet CSV — the model reads them and answers next to the company data.
- AI summaries on the Tasks and Reports pages; rule-based answers on the same real data if no AI key is set.

## Features
| Employee | Accountant |
|---|---|
| Dashboard: earnings, projects, weekly activity, team | Dashboard: payroll balance, pending approvals, budget, AI risk alerts, compliance, cash flow with forecast |
| Time tracker and task board | Tasks and payroll: hours, tasks and projects per employee, salary, **Pay** via PayPal Mass Pay |
| Wallet: balance, payout history, withdraw | Finance: PayPal balance, reserved funds, upcoming payments, department budgets |
| PayPal transactions | PayPal transactions |
| AI Burmalda chat | AI Burmalda chat — the payroll agent |
| Reports (CSV) | Reports (CSV): payroll, tax withholding, cash flow, department spend, compliance |

Light / dark / system theme.

## Quick start
Requirements: **JDK 21+** (IntelliJ IDEA recommended), nothing else — the database is an embedded H2 file.

1. Open the `backend` folder in IntelliJ IDEA (Maven loads by itself).
2. Run `BurmaldaApplication`.
3. Open **http://localhost:8080** — the backend serves this frontend too.

Demo accounts (password `demo1234`):

| Email | Role |
|---|---|
| `accountant@burmalda.com` | accountant |
| `engineer@burmalda.com` | employee |

The app starts with six months of demo data (dates relative to today).

**Optional keys** (environment variables in the run configuration) — without them PayPal payouts are simulated and the chat answers with rules:
- `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET` — PayPal sandbox app with *Payouts* enabled ([developer.paypal.com](https://developer.paypal.com))
- `ANTHROPIC_API_KEY` — Anthropic Claude key ([console.anthropic.com](https://console.anthropic.com)), or
- `AI_API_KEY` — Google Gemini key ([aistudio.google.com](https://aistudio.google.com)); if both are set, Claude is used

Details: [backend/README.md](backend/README.md). Check http://localhost:8080/api/health — it shows whether PayPal and AI are live.

### Docker
```bash
docker build -t burmalda .
docker run -p 8080:8080 -e PAYPAL_CLIENT_ID=... -e PAYPAL_CLIENT_SECRET=... -e AI_API_KEY=... burmalda
```

### Deploy to Render
The repository has a [`render.yaml`](render.yaml) blueprint: Render dashboard → **New → Blueprint** → pick this repository → enter the PayPal and Gemini keys. Every start comes with fresh demo data; the H2 console is switched off on the public deployment.

## Tech stack
- **Backend:** Java 21, Spring Boot 3.5, Spring Security + JWT, Spring Data JPA, H2 / PostgreSQL, Maven
- **Frontend:** plain HTML, CSS and JavaScript, no build step
- **PayPal:** Payouts API (sandbox)
- **AI:** Anthropic Claude (Messages API, tool use) or Google Gemini (function calling)

## Repository structure
```
index.html, login.html          — sign in / sign up (employee or accountant)
employee.html, accountant.html  — the two workspaces
css/style.css                   — styles and themes
js/api.js                       — all calls to the backend (CONFIG.USE_MOCK = true runs the UI on mock data)
js/common.js, js/pages.js       — layout, modals, charts, AI chat, transactions, reports
js/employee.js, js/accountant.js, js/auth.js, js/theme.js
backend/                        — Spring Boot server: API, payroll, PayPal, AI agent (see backend/README.md)
```

## License
[MIT](LICENSE)
