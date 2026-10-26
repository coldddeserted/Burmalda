/* =====================================================================
   Burmalda — слой API.
   Весь фронтенд ходит на сервер ТОЛЬКО через объект API ниже.
   Сейчас работает мок на localStorage (CONFIG.USE_MOCK = true).
   Для подключения бэкенда: поставить USE_MOCK = false и реализовать
   эндпоинты из README.md (контракты уже описаны в функциях ниже).
   ===================================================================== */
const CONFIG = {
  USE_MOCK: true,
  BASE_URL: '/api'
};

const LS = {
  users: 'burmalda_users',
  session: 'burmalda_session',
  token: 'burmalda_token'
};

function readJSON(key, fallback) {
  try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch (e) { return fallback; }
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/* ---------- реальный HTTP-клиент (используется при USE_MOCK = false) ---------- */
async function http(method, path, body) {
  const headers = { 'Content-Type': 'application/json' };
  const token = localStorage.getItem(LS.token);
  if (token) headers.Authorization = 'Bearer ' + token;
  const res = await fetch(CONFIG.BASE_URL + path, {
    method, headers, body: body ? JSON.stringify(body) : undefined
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.message || 'Ошибка сервера (' + res.status + ')');
  return data;
}

/* ---------- мок-данные ---------- */
function seedUsers() {
  if (readJSON(LS.users, null)) return;
  localStorage.setItem(LS.users, JSON.stringify([
    { id: 1, name: 'Edgar Wilson', email: 'engineer@burmalda.com', password: 'demo1234', role: 'employee', department: 'Engineering', company: 'Burmalda' },
    { id: 2, name: 'Anna Kowalska', email: 'accountant@burmalda.com', password: 'demo1234', role: 'accountant', department: 'Finance', company: 'Burmalda' }
  ]));
}
seedUsers();

const MOCK = {
  employee(user) {
    return {
      totalEarned: { amount: 5000, currency: 'USD' },
      endedProjects: { count: 10, delta: 6 },
      runningProjects: { count: 12, delta: 2 },
      discussProjects: { count: 2, label: 'On Discuss' },
      analytics: [
        { day: 'S', value: 55, type: 'hatched' },
        { day: 'M', value: 72, type: 'light' },
        { day: 'T', value: 74, type: 'mid', tip: '74%' },
        { day: 'W', value: 100, type: 'dark' },
        { day: 'T', value: 88, type: 'hatched' },
        { day: 'F', value: 62, type: 'hatched' },
        { day: 'S', value: 78, type: 'hatched' }
      ],
      team: [
        { id: 1, name: 'Leon Isa', task: 'Responsive Layout for Homepage', status: 'progress' },
        { id: 2, name: user.name, task: 'Develop Search and Filter Functionality', status: 'pending' },
        { id: 3, name: 'Alexandra Kim', task: 'Integrate User Authentication System', status: 'progress' },
        { id: 4, name: 'Jack Lui', task: 'Github Project Repository', status: 'completed' }
      ],
      progress: { completed: 37, inProgress: 13, pending: 50 },
      timer: { seconds: 1 * 3600 + 24 * 60 + 8 },
      detail: typeof DETAIL !== "undefined" ? DETAIL.employee() : undefined
    };
  },
  accountant() {
    return {
      payroll: { balance: 124500, currency: 'USD', deltaPct: 4.2, prevMonth: 119400, prevLabel: 'Sept', weekly: [38, 52, 61, 90] },
      pendingApprovals: { amount: 14670, currency: 'USD' },
      budget: { amount: 70750, currency: 'USD', splits: [45000, 18500, 7250] },
      risk: { anomalies: 2 },
      compliance: { percent: 92, items: ['Employee Profiles (45/50)', 'Tax Report (Q3) - Submitted'] },
      cashFlow: {
        inflow:  [18, 34, 46, 52, 30, 14, 40, 78, 60, 90],
        outflow: [10, 22, 30, 38, 20, 10, 30, 58, 48, 70],
        forecast:[14, 26, 38, 44, 36, 34, 36, 50, 40, 55],
        bars: [{ in: 42, out: 18 }, { in: 55, out: 28 }, { in: 62, out: 34 }, { in: 70, out: 40 }]
      },
      transactions: [
        { id: 1, name: 'Payroll - Engineering Dept', amount: 11900, date: '05.10.2026, 10:25 AM', status: 'Completed', badge: 'PR', color: '#5b7aa8' },
        { id: 2, name: 'Vendor: Slack Technologies', amount: 1200, date: '04.10.2026, 15:30 PM', status: 'Completed', badge: 'SL', color: '#7a4f8f' },
        { id: 3, name: 'Expense: Server Hosting', amount: 4350, date: '04.10.2026, 17:00 PM', status: 'Completed', badge: 'AW', color: '#c98a2b' },
        { id: 4, name: 'Vendor: Figma Team Plan', amount: 850, date: '03.10.2026, 18:30 PM', status: 'Completed', badge: 'FG', color: '#b4543f' },
        { id: 5, name: 'Cross-Border Transfer', amount: 2500, date: '03.10.2026, 08:30 AM', status: 'Completed', badge: 'XB', color: '#2f7d6a' },
        { id: 6, name: 'Quarterly Tax Withholding', amount: 6100, date: '02.10.2026, 10:30 AM', status: 'Completed', badge: 'TX', color: '#566073' }
      ],
      departments: [
        { name: 'Engineering', amount: 45000 }, { name: 'Marketing', amount: 18500 },
        { name: 'Sales', amount: 12000 }, { name: 'Design', amount: 9000 }, { name: 'Operations', amount: 7250 }
      ],
      savings: { total: 4250, deltaPct: 18, weeks: [38, 52, 70, 100] },
      detail: typeof DETAIL !== "undefined" ? DETAIL.accountant() : undefined
    };
  },
  aiReply(q, role) {
    const t = q.toLowerCase();
    if (role === 'accountant') {
      if (/risk|anomal|риск|аном/.test(t)) return 'Сейчас AI отметил 2 аномальные транзакции. Откройте «Review Issues» на карточке AI Risk Alerts.';
      if (/tax|налог/.test(t)) return 'Налоговый отчёт Q3 отправлен. Осталось подтвердить 5 профилей сотрудников (45/50).';
      return 'Это демо-ответ. После подключения бэкенда здесь будет настоящий AI-помощник по финансам.';
    }
    if (/task|задач/.test(t)) return 'У вас 1 задача в статусе Pending: «Develop Search and Filter Functionality».';
    return 'Это демо-ответ. После подключения бэкенда здесь будет настоящий AI-ассистент по проектам.';
  }
};

/* ---------- мок-данные v2 (страницы, подробности виджетов) ---------- */
const MOCK2 = {
  employeeDetails() {
    const endedNames = ['Landing page redesign', 'Mobile app onboarding', 'Billing API v2', 'Admin dashboard', 'Email templates', 'Design system v1', 'Payment gateway SDK', 'Search indexing', 'Docs portal', 'Analytics export'];
    const runNames = ['Responsive Layout for Homepage', 'Search and Filter Functionality', 'User Authentication System', 'GitHub Project Repository', 'Notification service', 'CI/CD pipeline', 'Invoice PDF generator', 'Customer portal', 'Role permissions', 'Onboarding emails', 'Mobile push', 'Audit log'];
    const prog = [82, 45, 70, 100, 30, 64, 52, 18, 76, 40, 25, 58];
    return {
      details: {
        payouts: [
          { date: '31.10.2026', label: 'October salary', amount: 3200, status: 'Scheduled' },
          { date: '01.10.2026', label: 'September salary', amount: 3200, status: 'Paid' },
          { date: '15.09.2026', label: 'Bonus: Q3 milestone', amount: 800, status: 'Paid' },
          { date: '01.09.2026', label: 'August salary', amount: 3200, status: 'Paid' }
        ],
        ended: endedNames.map((n, i) => ({ name: n, date: String(28 - i * 2).padStart(2, '0') + '.' + (i < 5 ? '10' : '09') + '.2026', pay: 400 + i * 85 })),
        running: runNames.map((n, i) => ({ name: n, progress: prog[i], due: String(3 + i * 2).padStart(2, '0') + '.' + (i < 8 ? '11' : '12') + '.2026' })),
        discuss: [
          { name: 'Partner API integration', note: 'Waiting for scope from the client' },
          { name: 'Data warehouse migration', note: 'Budget approval pending' }
        ],
        sessions: [
          { project: 'Responsive Layout for Homepage', from: '09:05', dur: '02:10' },
          { project: 'Search and Filter Functionality', from: '11:40', dur: '01:05' },
          { project: 'Code review', from: '13:30', dur: '00:42' }
        ]
      }
    };
  },
  accountantDetails() {
    return {
      details: {
        pending: [
          { name: 'Mass Pay: Marketing Dept', amount: 6800, by: 'Sofia Rossi', due: '31.10.2026' },
          { name: 'Expense: Server Hosting (Oct)', amount: 4350, by: 'Leon Isa', due: '28.10.2026' },
          { name: 'Cross-Border Transfer: Lisbon contractor', amount: 2500, by: 'Maria Santos', due: '27.10.2026' },
          { name: 'Vendor: Notion + Linear', amount: 1020, by: 'Daniel Park', due: '30.10.2026' }
        ],
        alloc: [
          { dept: 'Engineering', allocated: 52000, spent: 45000, heads: 18 },
          { dept: 'Marketing', allocated: 24000, spent: 18500, heads: 8 },
          { dept: 'Sales', allocated: 15000, spent: 12000, heads: 10 },
          { dept: 'Design', allocated: 12000, spent: 9000, heads: 6 },
          { dept: 'Operations', allocated: 9000, spent: 7250, heads: 3 }
        ],
        anomalies: [
          { title: 'Duplicate vendor payment', detail: 'Slack Technologies was charged twice on 04.10.2026.', amount: 1200, level: 'High' },
          { title: 'Unusual transfer amount', detail: 'Cross-border transfer is 2.4x above the average for this payee.', amount: 2500, level: 'High' }
        ],
        docs: [
          { name: 'Employee profiles', status: '45 / 50', ok: false },
          { name: 'Tax report Q3', status: 'Submitted', ok: true },
          { name: 'PayPal business verification', status: 'Verified', ok: true },
          { name: 'Contractor tax forms', status: '3 missing', ok: false }
        ],
        savings: [
          { label: 'Mass Pay instead of single payouts', amount: 2650 },
          { label: 'Smart FX routing', amount: 1600 }
        ]
      }
    };
  },
  tasks() {
    return [
      { id: 1, title: 'Develop Search and Filter Functionality', project: 'Customer portal', due: '29.10.2026', prio: 'high', status: 'todo' },
      { id: 2, title: 'Write unit tests for auth module', project: 'User Authentication System', due: '30.10.2026', prio: 'mid', status: 'todo' },
      { id: 3, title: 'Review pull request #218', project: 'GitHub Project Repository', due: '27.10.2026', prio: 'low', status: 'todo' },
      { id: 4, title: 'Responsive layout for pricing page', project: 'Responsive Layout for Homepage', due: '01.11.2026', prio: 'mid', status: 'progress' },
      { id: 5, title: 'Set up CI cache for builds', project: 'CI/CD pipeline', due: '03.11.2026', prio: 'low', status: 'progress' },
      { id: 6, title: 'Fix invoice PDF page breaks', project: 'Invoice PDF generator', due: '25.10.2026', prio: 'high', status: 'progress' },
      { id: 7, title: 'Export analytics to CSV', project: 'Analytics export', due: '20.10.2026', prio: 'mid', status: 'done' },
      { id: 8, title: 'Docs portal: search page', project: 'Docs portal', due: '16.10.2026', prio: 'low', status: 'done' }
    ];
  },
  team() {
    return {
      period: 'October 2026',
      employees: [
        { id: 1, name: 'Leon Isa', dept: 'Engineering', tasksDone: 14, tasksTotal: 16, projectsEnded: 2, hours: 168, salary: 4200, status: 'Unpaid' },
        { id: 2, name: 'Edgar Wilson', dept: 'Engineering', tasksDone: 11, tasksTotal: 15, projectsEnded: 1, hours: 160, salary: 3200, status: 'Unpaid' },
        { id: 3, name: 'Alexandra Kim', dept: 'Engineering', tasksDone: 18, tasksTotal: 20, projectsEnded: 3, hours: 172, salary: 3800, status: 'Unpaid' },
        { id: 4, name: 'Jack Lui', dept: 'Engineering', tasksDone: 9, tasksTotal: 9, projectsEnded: 1, hours: 150, salary: 3000, status: 'Paid' },
        { id: 5, name: 'Maria Santos', dept: 'Design', tasksDone: 12, tasksTotal: 14, projectsEnded: 2, hours: 164, salary: 3400, status: 'Unpaid' },
        { id: 6, name: 'Tomasz Nowak', dept: 'Sales', tasksDone: 7, tasksTotal: 10, projectsEnded: 1, hours: 158, salary: 2900, status: 'Unpaid' },
        { id: 7, name: 'Sofia Rossi', dept: 'Marketing', tasksDone: 15, tasksTotal: 17, projectsEnded: 2, hours: 166, salary: 3300, status: 'Unpaid' },
        { id: 8, name: 'Daniel Park', dept: 'Operations', tasksDone: 6, tasksTotal: 8, projectsEnded: 0, hours: 155, salary: 2700, status: 'Unpaid' }
      ],
      projects: [
        { name: 'Billing API v2', lead: 'Alexandra Kim', date: '21.10.2026', budget: 18000 },
        { name: 'Customer portal beta', lead: 'Leon Isa', date: '18.10.2026', budget: 24500 },
        { name: 'Brand refresh', lead: 'Maria Santos', date: '14.10.2026', budget: 9200 },
        { name: 'Partner onboarding flow', lead: 'Sofia Rossi', date: '09.10.2026', budget: 7400 },
        { name: 'Admin dashboard', lead: 'Edgar Wilson', date: '06.10.2026', budget: 12800 },
        { name: 'Repository migration', lead: 'Jack Lui', date: '02.10.2026', budget: 5100 }
      ]
    };
  },
  summary(role) {
    if (role === 'accountant') return {
      updated: 'Today, 09:12',
      points: [
        { tone: 'warn', text: 'Payroll due this month is $23,500 for 7 unpaid employees. Jack Lui is already paid.' },
        { tone: 'good', text: 'Alexandra Kim closed the most tasks (18 of 20) and 3 projects. Her bonus criteria are met.' },
        { tone: 'warn', text: 'Daniel Park finished 6 of 8 tasks and no projects. Check workload before approving overtime.' },
        { tone: 'good', text: 'Paying everyone in one PayPal Mass Pay batch saves about $2,650 compared with single payouts.' },
        { tone: '', text: '2 flagged transactions are still open. Review them before the payroll run on 31.10.' }
      ]
    };
    return {
      updated: 'Today, 09:12',
      points: [
        { tone: 'good', text: 'You logged 47.7 hours this week, 6% more than last week.' },
        { tone: 'warn', text: '"Fix invoice PDF page breaks" is due on 25.10 and is still in progress.' },
        { tone: 'good', text: 'Earnings this month are on track: October salary of $3,200 is scheduled for 31.10.' },
        { tone: '', text: '10 projects are finished this month, 6 more than in September.' }
      ]
    };
  },
  wallet(user) {
    return {
      balance: 5000, currency: 'USD', pending: 3200, nextPayday: '31.10.2026',
      paypal: { email: user.email, verified: true },
      months: [{ m: 'May', v: 3000 }, { m: 'Jun', v: 3000 }, { m: 'Jul', v: 3400 }, { m: 'Aug', v: 3200 }, { m: 'Sep', v: 4000 }, { m: 'Oct', v: 3200 }],
      history: [
        { date: '01.10.2026', label: 'September salary', amount: 3200, status: 'Paid' },
        { date: '15.09.2026', label: 'Bonus: Q3 milestone', amount: 800, status: 'Paid' },
        { date: '01.09.2026', label: 'August salary', amount: 3200, status: 'Paid' },
        { date: '12.08.2026', label: 'Expense refund: conference', amount: 240, status: 'Paid' },
        { date: '01.08.2026', label: 'July salary', amount: 3400, status: 'Paid' }
      ]
    };
  },
  finance() {
    return {
      paypalBalance: 84250, available: 70750, reserved: 13500, currency: 'USD',
      bank: 'Bank account ending 4417',
      upcoming: [
        { label: 'Payroll run (Mass Pay)', date: '31.10.2026', amount: 23500, kind: 'Payroll' },
        { label: 'Quarterly tax withholding', date: '15.11.2026', amount: 6100, kind: 'Tax' },
        { label: 'Slack Technologies', date: '04.11.2026', amount: 1200, kind: 'Vendor' },
        { label: 'Server hosting', date: '04.11.2026', amount: 4350, kind: 'Vendor' }
      ],
      alloc: MOCK2.accountantDetails().details.alloc,
      months: [{ m: 'May', v: 98000 }, { m: 'Jun', v: 104000 }, { m: 'Jul', v: 109000 }, { m: 'Aug', v: 112500 }, { m: 'Sep', v: 119400 }, { m: 'Oct', v: 124500 }]
    };
  },
  reports(role) {
    if (role === 'accountant') return [
      { id: 'payroll', name: 'Payroll summary', desc: 'Salaries, bonuses and PayPal Mass Pay batches for the selected period.', formats: ['PDF', 'CSV'] },
      { id: 'tax', name: 'Tax withholding', desc: 'Withheld amounts per employee and quarterly totals ready for filing.', formats: ['PDF', 'CSV'] },
      { id: 'cash', name: 'Cash flow', desc: 'Inflow, outflow and forecast by week from PayPal activity.', formats: ['PDF', 'XLSX'] },
      { id: 'dept', name: 'Department spend', desc: 'Allocated budget against actual spending for every department.', formats: ['PDF', 'XLSX'] },
      { id: 'perf', name: 'Tasks and projects', desc: 'Completed tasks and finished projects per employee, linked to pay.', formats: ['PDF', 'CSV'] },
      { id: 'compliance', name: 'Compliance audit', desc: 'Missing documents, verification status and flagged transactions.', formats: ['PDF'] }
    ];
    return [
      { id: 'earn', name: 'Earnings statement', desc: 'Salary, bonuses and refunds you received through PayPal.', formats: ['PDF', 'CSV'] },
      { id: 'time', name: 'Time sheet', desc: 'Hours per day and per project from the time tracker.', formats: ['PDF', 'CSV'] },
      { id: 'proj', name: 'Project summary', desc: 'Finished and running projects with your share of the work.', formats: ['PDF'] },
      { id: 'tax', name: 'Annual tax summary', desc: 'Yearly income overview for your tax return.', formats: ['PDF'] }
    ];
  },
  transactions(role) {
    if (role === 'accountant') return {
      balance: 84250,
      items: [
        { id: '8XK21094AB552140', date: '2026-10-05 10:25', name: 'Mass Pay: Engineering Dept', note: 'Payroll, 4 recipients', dir: 'out', amount: 11900, fee: 119, status: 'Completed' },
        { id: '3FD77120LM990312', date: '2026-10-04 15:30', name: 'Slack Technologies', note: 'Vendor, monthly plan', dir: 'out', amount: 1200, fee: 0, status: 'Completed' },
        { id: '5TY88231QW114503', date: '2026-10-04 17:00', name: 'Server Hosting', note: 'Expense, October', dir: 'out', amount: 4350, fee: 0, status: 'Completed' },
        { id: '2NB10945RE770281', date: '2026-10-03 18:30', name: 'Figma Team Plan', note: 'Vendor', dir: 'out', amount: 850, fee: 0, status: 'Completed' },
        { id: '9CV55301ZX204468', date: '2026-10-03 08:30', name: 'Cross-Border Transfer', note: 'Lisbon contractor, EUR', dir: 'out', amount: 2500, fee: 37.5, status: 'Completed' },
        { id: '7HG20987PO331190', date: '2026-10-02 10:30', name: 'Quarterly Tax Withholding', note: 'Tax authority', dir: 'out', amount: 6100, fee: 0, status: 'Completed' },
        { id: '1AQ66402SD812357', date: '2026-10-01 09:00', name: 'Funding from bank', note: 'Bank account ending 4417', dir: 'in', amount: 40000, fee: 0, status: 'Completed' },
        { id: '6MJ31876KL095524', date: '2026-09-29 14:12', name: 'Northwind Ltd', note: 'Client payment, invoice 2041', dir: 'in', amount: 18500, fee: 647.5, status: 'Completed' },
        { id: '4PL90213TG668801', date: '2026-09-28 11:48', name: 'Mass Pay: Design Dept', note: 'Payroll, 5 recipients', dir: 'out', amount: 4200, fee: 42, status: 'Completed' },
        { id: '0WS44718HB529066', date: '2026-09-26 16:05', name: 'Figma', note: 'Refund', dir: 'in', amount: 120, fee: 0, status: 'Completed' },
        { id: '8RE12609CX773415', date: '2026-10-31 09:00', name: 'Mass Pay: Marketing Dept', note: 'Payroll, scheduled', dir: 'out', amount: 6800, fee: 68, status: 'Pending' },
        { id: '3KD58830VN146972', date: '2026-09-22 13:20', name: 'Notion', note: 'Vendor, card declined', dir: 'out', amount: 360, fee: 0, status: 'Failed' }
      ]
    };
    return {
      balance: 5000,
      items: [
        { id: '8XK21094AB552140', date: '2026-10-31 09:00', name: 'Burmalda Inc.', note: 'October salary', dir: 'in', amount: 3200, fee: 0, status: 'Pending' },
        { id: '3FD77120LM990312', date: '2026-10-01 09:00', name: 'Burmalda Inc.', note: 'September salary', dir: 'in', amount: 3200, fee: 0, status: 'Completed' },
        { id: '5TY88231QW114503', date: '2026-09-20 12:40', name: 'Withdrawal to bank', note: 'Bank account ending 4417', dir: 'out', amount: 2000, fee: 0, status: 'Completed' },
        { id: '2NB10945RE770281', date: '2026-09-15 10:10', name: 'Burmalda Inc.', note: 'Bonus: Q3 milestone', dir: 'in', amount: 800, fee: 0, status: 'Completed' },
        { id: '9CV55301ZX204468', date: '2026-09-01 09:00', name: 'Burmalda Inc.', note: 'August salary', dir: 'in', amount: 3200, fee: 0, status: 'Completed' },
        { id: '7HG20987PO331190', date: '2026-08-28 17:25', name: 'Withdrawal to bank', note: 'Bank account ending 4417', dir: 'out', amount: 1500, fee: 0, status: 'Completed' },
        { id: '1AQ66402SD812357', date: '2026-08-12 15:02', name: 'Burmalda Inc.', note: 'Expense refund: conference', dir: 'in', amount: 240, fee: 0, status: 'Completed' },
        { id: '6MJ31876KL095524', date: '2026-08-05 11:30', name: 'Withdrawal to bank', note: 'Rejected by bank', dir: 'out', amount: 1000, fee: 0, status: 'Failed' }
      ]
    };
  },
  suggestions(role) {
    return role === 'accountant'
      ? ['Show pending approvals', 'How much do I owe in salaries this month?', 'Which payments look unusual?', 'Summarise cash flow for October', 'How much did Mass Pay save us?']
      : ['When is my next payday?', 'Show my last PayPal payouts', 'How many hours did I log this week?', 'How do I withdraw to my bank?'];
  },
  history(role) {
    return role === 'accountant'
      ? ['Payroll run for October', 'Anomalies in vendor payments', 'Q3 tax withholding check']
      : ['Next payday', 'Withdrawal to bank', 'Hours report for September'];
  },
  aiReply(q, role) {
    const t = q.toLowerCase();
    const tail = '\n\n(Demo reply. The PayPal AI agent will answer here once the backend is connected.)';
    if (role === 'accountant') {
      if (/pending|approv|одобр/.test(t)) return 'There are 4 payments waiting for approval, 14,670.00 USD in total. The largest is Mass Pay for the Marketing Dept (6,800.00 USD, due 31.10).' + tail;
      if (/salar|payroll|зарплат|owe/.test(t)) return 'Unpaid salaries for October: 23,500.00 USD for 7 employees. Jack Lui is already paid. You can send all of it in one Mass Pay batch from the Tasks page.' + tail;
      if (/unusual|anomal|risk|аном|риск/.test(t)) return 'Two payments are flagged: a duplicate Slack Technologies charge (1,200.00 USD) and a cross-border transfer 2.4x above average (2,500.00 USD).' + tail;
      if (/cash|flow|поток/.test(t)) return 'October inflow is higher than outflow in weeks 3 and 4. The forecast shows a dip in week 2 of November because of the tax payment on 15.11.' + tail;
      if (/save|saving|mass|эконом/.test(t)) return 'Mass Pay and Smart FX routing saved 4,250.00 USD this month, 18% more than in September.' + tail;
      return 'I can help with payroll, pending approvals, unusual payments and cash flow. Try one of the suggestions below.' + tail;
    }
    if (/payday|salary|зарплат/.test(t)) return 'Your next salary of 3,200.00 USD is scheduled for 31.10.2026 and will arrive on your PayPal account.' + tail;
    if (/payout|paypal|last/.test(t)) return 'Last payouts: 3,200.00 USD on 01.10 (September salary), 800.00 USD on 15.09 (Q3 bonus) and 3,200.00 USD on 01.09.' + tail;
    if (/hour|time|час/.test(t)) return 'This week you logged 47.7 hours. Wednesday was your longest day with 9 hours.' + tail;
    if (/withdraw|bank|вывод|вывест/.test(t)) return 'Open My wallet, press Withdraw to PayPal, enter the amount and confirm. Transfers to your bank account usually take 1 to 3 business days.' + tail;
    return 'I can help with your payouts, payday and hours. Try one of the suggestions below.' + tail;
  }
};

/* ---------- публичный API ---------- */
const API = {
  /** POST /auth/login  {email, password} -> {token, user:{id,name,email,role,department,company}} */
  async login(email, password) {
    if (!CONFIG.USE_MOCK) {
      const r = await http('POST', '/auth/login', { email, password });
      localStorage.setItem(LS.token, r.token);
      localStorage.setItem(LS.session, JSON.stringify(r.user));
      return r.user;
    }
    await sleep(450);
    const u = readJSON(LS.users, []).find((x) => x.email.toLowerCase() === email.toLowerCase());
    if (!u || u.password !== password) throw new Error('Неверный email или пароль');
    const { password: _p, ...user } = u;
    localStorage.setItem(LS.session, JSON.stringify(user));
    return user;
  },

  /** POST /auth/register {name,email,password,role:'accountant'|'employee',department,company} -> {user} */
  async register(data) {
    if (!CONFIG.USE_MOCK) return http('POST', '/auth/register', data);
    await sleep(500);
    const users = readJSON(LS.users, []);
    if (users.some((x) => x.email.toLowerCase() === data.email.toLowerCase()))
      throw new Error('Пользователь с таким email уже существует');
    const user = { id: Date.now(), ...data };
    users.push(user);
    localStorage.setItem(LS.users, JSON.stringify(users));
    const { password: _p, ...safe } = user;
    return safe;
  },

  /** Текущий пользователь из сессии (null если не вошёл). Для реального бэка можно заменить на GET /auth/me */
  currentUser() { return readJSON(LS.session, null); },

  /** POST /auth/logout */
  async logout() {
    if (!CONFIG.USE_MOCK) { try { await http('POST', '/auth/logout'); } catch (e) {} }
    localStorage.removeItem(LS.session);
    localStorage.removeItem(LS.token);
  },

  /** GET /dashboard/employee */
  async employeeDashboard() {
    if (!CONFIG.USE_MOCK) return http('GET', '/dashboard/employee');
    await sleep(250);
    return { ...MOCK.employee(API.currentUser()), ...MOCK2.employeeDetails() };
  },

  /** GET /dashboard/accountant */
  async accountantDashboard() {
    if (!CONFIG.USE_MOCK) return http('GET', '/dashboard/accountant');
    await sleep(250);
    return { ...MOCK.accountant(), ...MOCK2.accountantDetails() };
  },

  /** POST /ai/chat {message, role} -> {reply} */
  async askAI(message, role) {
    if (!CONFIG.USE_MOCK) return (await http('POST', '/ai/chat', { message, role })).reply;
    await sleep(700);
    return MOCK2.aiReply(message, role);
  },

  /** POST /team/members {name,email,task} -> member */
  async addMember(m) {
    if (!CONFIG.USE_MOCK) return http('POST', '/team/members', m);
    await sleep(300);
    return { id: Date.now(), name: m.name, task: m.task || 'New task', status: 'pending' };
  },

  /** POST /wallet/withdraw {amount} */
  async withdraw(amount) {
    if (!CONFIG.USE_MOCK) return http('POST', '/wallet/withdraw', { amount });
    await sleep(400);
    return { ok: true };
  },

  /** GET /search?q= */
  async search(q) {
    if (!CONFIG.USE_MOCK) return http('GET', '/search?q=' + encodeURIComponent(q));
    await sleep(200);
    return [];
  },

  /** POST /time/log {seconds} — сохранить учтённое время */
  async logTime(seconds) {
    if (!CONFIG.USE_MOCK) return http('POST', '/time/log', { seconds });
    return { ok: true };
  },

  /** GET /ai/suggestions?role= -> string[]   (подсказки в чате AI Burmalda) */
  async aiSuggestions(role) {
    if (!CONFIG.USE_MOCK) return http('GET', '/ai/suggestions?role=' + role);
    return MOCK2.suggestions(role);
  },
  /** GET /ai/history?role= -> string[]   (прошлые диалоги с PayPal AI агентом) */
  async aiHistory(role) {
    if (!CONFIG.USE_MOCK) return http('GET', '/ai/history?role=' + role);
    return MOCK2.history(role);
  },
  /** GET /ai/summary?role= -> {updated, points:[{tone,text}]}   (AI-сводка для Tasks / Reports) */
  async aiSummary(role) {
    if (!CONFIG.USE_MOCK) return http('GET', '/ai/summary?role=' + role);
    await sleep(200);
    return MOCK2.summary(role);
  },

  /** GET /paypal/transactions -> {balance, items:[{id,date,name,note,dir:'in'|'out',amount,fee,status}]}   (только PayPal) */
  async transactions(role) {
    if (!CONFIG.USE_MOCK) return http('GET', '/paypal/transactions');
    await sleep(200);
    return MOCK2.transactions(role);
  },

  /** GET /tasks/mine -> task[]  /  PATCH /tasks/:id {status:'todo'|'progress'|'done'} */
  async myTasks() {
    if (!CONFIG.USE_MOCK) return http('GET', '/tasks/mine');
    await sleep(150);
    const saved = readJSON('burmalda_tasks', null);
    return saved || MOCK2.tasks();
  },
  async updateTask(id, status) {
    if (!CONFIG.USE_MOCK) return http('PATCH', '/tasks/' + id, { status });
    const list = readJSON('burmalda_tasks', null) || MOCK2.tasks();
    const t = list.find((x) => x.id === id); if (t) t.status = status;
    localStorage.setItem('burmalda_tasks', JSON.stringify(list));
    return { ok: true };
  },

  /** GET /wallet -> {balance,pending,nextPayday,paypal,months,history} */
  async wallet() {
    if (!CONFIG.USE_MOCK) return http('GET', '/wallet');
    await sleep(150);
    return MOCK2.wallet(API.currentUser());
  },

  /** GET /accounting/team?period= -> {period,employees,projects}   (бухгалтер: задачи, проекты, зарплаты) */
  async team() {
    if (!CONFIG.USE_MOCK) return http('GET', '/accounting/team');
    await sleep(200);
    const data = MOCK2.team();
    const paid = readJSON('burmalda_paid', []);
    data.employees.forEach((e) => { if (paid.includes(e.id)) e.status = 'Paid'; });
    return data;
  },
  /** POST /accounting/pay {ids:number[]} -> {ok, batchId}   (PayPal Mass Pay) */
  async payEmployees(ids) {
    if (!CONFIG.USE_MOCK) return http('POST', '/accounting/pay', { ids });
    await sleep(600);
    const paid = readJSON('burmalda_paid', []);
    localStorage.setItem('burmalda_paid', JSON.stringify([...new Set([...paid, ...ids])]));
    return { ok: true, batchId: 'MP-' + Date.now().toString().slice(-6) };
  },

  /** GET /accounting/finance -> {paypalBalance,available,reserved,upcoming,alloc,months} */
  async finance() {
    if (!CONFIG.USE_MOCK) return http('GET', '/accounting/finance');
    await sleep(200);
    return MOCK2.finance();
  },
  /** POST /accounting/funds {amount} */
  async addFunds(amount) {
    if (!CONFIG.USE_MOCK) return http('POST', '/accounting/funds', { amount });
    await sleep(400); return { ok: true };
  },
  /** POST /accounting/payment {to,amount,note} */
  async sendPayment(p) {
    if (!CONFIG.USE_MOCK) return http('POST', '/accounting/payment', p);
    await sleep(400); return { ok: true };
  },

  /** GET /reports -> [{id,name,desc,formats}]  /  POST /reports/:id/generate {format,period} */
  async reports(role) {
    if (!CONFIG.USE_MOCK) return http('GET', '/reports');
    await sleep(150);
    return MOCK2.reports(role);
  },
  async generateReport(id, format, period) {
    if (!CONFIG.USE_MOCK) return http('POST', '/reports/' + id + '/generate', { format, period });
    await sleep(500); return { ok: true };
  }
};
