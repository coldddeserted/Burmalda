/* Дашборд бухгалтера */


function sparkline(vals) {
  const w = 230, h = 120;
  const pts = seriesPoints(vals, w, h - 20, 22, 100).map(([x, y]) => [x, y + 8]);
  const line = smoothPath(pts);
  return `<svg viewBox="0 0 ${w} ${h + 18}" width="100%" aria-hidden="true">
    <defs><linearGradient id="spk" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="var(--accent)" stop-opacity=".7"/><stop offset="1" stop-color="var(--accent)" stop-opacity="0"/></linearGradient></defs>
    <path d="${line} L${pts.at(-1)[0]},${h} L${pts[0][0]},${h} Z" fill="url(#spk)"/>
    <path d="${line}" fill="none" stroke="var(--accent-2)" stroke-width="2"/>
    ${pts.map((p) => `<circle cx="${p[0]}" cy="${p[1]}" r="3" fill="var(--surface)" stroke="var(--accent-2)" stroke-width="1.5"/>`).join('')}
    ${['Wk 1', 'Wk 2', 'Wk 3', 'Wk 4'].map((t, i) => `<text x="${pts[i][0]}" y="${h + 14}" text-anchor="middle" font-size="10" fill="var(--text)">${t}</text>`).join('')}
  </svg>`;
}

function cashFlow(cf) {
  const W = 720, VH = 520;
  const lineTop = 32, lineBot = 280;
  const lineH = lineBot - lineTop;
  const mk = (v) => seriesPoints(v, W - 48, lineH - 20, 20, 100).map(([x, y]) => [x, y + lineTop]);
  const pi = mk(cf.inflow), po = mk(cf.outflow), pf = mk(cf.forecast);
  const li = smoothPath(pi), lo = smoothPath(po), lf = smoothPath(pf);
  const base = lineBot - 8;
  const zero = 390, bw = 38, scale = 1.45;
  const labelY = VH - 14;
  const bars = cf.bars.map((b, i) => {
    const cx = 110 + i * 170;
    const inH = b.in * scale, outH = b.out * scale;
    return `<rect x="${cx - bw}" y="${zero - inH}" width="${bw}" height="${inH}" fill="var(--accent-2)" opacity=".75"/>
            <rect x="${cx + 2}" y="${zero}" width="${bw}" height="${outH}" fill="#6b7280" opacity=".9"/>
            <text x="${cx}" y="${labelY}" text-anchor="middle" font-size="12" fill="var(--muted)">Wk ${i + 1}</text>`;
  }).join('');
  const gy = [lineTop, (lineTop + base) / 2, base];
  return `<div class="cf-chart-wrap">
    <svg class="cf-chart" viewBox="0 0 ${W} ${VH}" preserveAspectRatio="none" role="img" aria-label="Cash flow chart">
      <defs><linearGradient id="cfg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="var(--accent)" stop-opacity=".75"/><stop offset="1" stop-color="var(--accent)" stop-opacity=".05"/></linearGradient></defs>
      ${gy.map((y) => `<line x1="0" x2="${W}" y1="${y}" y2="${y}" stroke="var(--border)"/>`).join('')}
      <text x="0" y="${gy[0] - 8}" font-size="14" font-weight="600" fill="var(--text)">$100k</text>
      <text x="0" y="${gy[1] - 8}" font-size="14" font-weight="600" fill="var(--text)">$50k</text>
      <text x="0" y="${gy[2] - 8}" font-size="14" font-weight="600" fill="var(--text)">$10k</text>
      <g transform="translate(48 0)">
        <path d="${li} L${pi.at(-1)[0]},${base} L${pi[0][0]},${base} Z" fill="url(#cfg)" opacity=".8"/>
        <path d="${li}" fill="none" stroke="var(--text)" stroke-width="2.4"/>
        <path d="${lo}" fill="none" stroke="#6b7280" stroke-width="2.2"/>
        <path d="${lf}" fill="none" stroke="var(--text)" stroke-width="2" stroke-dasharray="2 5" stroke-linecap="round"/>
        ${[3, 5, 8].map((i) => `<circle cx="${pi[i][0]}" cy="${pi[i][1]}" r="4" fill="var(--surface)" stroke="var(--text)" stroke-width="2"/>`).join('')}
        <circle cx="${pf[5][0]}" cy="${pf[5][1]}" r="4" fill="var(--surface)" stroke="var(--text)" stroke-width="2"/>
      </g>
      <g transform="translate(20 0)">
        <line x1="0" x2="${W - 20}" y1="${zero}" y2="${zero}" stroke="var(--border)"/>
        ${bars}
      </g>
      <text x="0" y="${zero - 70}" font-size="12" fill="var(--muted)">$100k</text>
      <text x="0" y="${zero + 4}" font-size="12" fill="var(--muted)">0</text>
      <text x="0" y="${zero + 70}" font-size="12" fill="var(--muted)">$10k</text>
    </svg>
  </div>`;
}

function donut(pct) {
  const r = 50, c = 2 * Math.PI * r, on = (pct / 100) * c;
  return `<div class="donut"><svg viewBox="0 0 132 132" width="132" height="132" style="transform:rotate(-90deg)">
    <circle cx="66" cy="66" r="${r}" fill="none" stroke="var(--surface-2)" stroke-width="16"/>
    <circle cx="66" cy="66" r="${r}" fill="none" stroke="var(--accent)" stroke-width="16" stroke-dasharray="${on.toFixed(1)} ${(c - on).toFixed(1)}" stroke-linecap="butt"/>
  </svg><div class="dc"><b>${pct}%</b><span>Verified</span></div></div>`;
}

const txRow = (t) => `<div class="tx">
  <div class="ti" style="background:${esc(t.color)}">${esc(t.badge)}</div>
  <div><div class="tn">${esc(t.name)}</div><div class="td">${esc(t.date)} - USD</div></div>
  <div class="ta">$${money(t.amount, 2)}<small>${esc(t.status)}</small></div></div>`;

async function renderDashboard(_id, view) {
  view.innerHTML = '<p class="sub">Загрузка…</p>';
  let d;
  try { d = await API.accountantDashboard(); } catch (e) { view.innerHTML = `<p class="sub">Ошибка: ${esc(e.message)}</p>`; return; }
  const maxDept = Math.max(...d.departments.map((x) => x.amount));
  const [s1, s2, s3] = d.budget.splits;

  view.innerHTML = `
  <div class="grid g-top-acc" style="grid-template-columns:1.5fr 1fr 1fr">
    <div class="card">
      <h2>Total Payroll Balance</h2>
      <div class="pay-top">
        <div>
          <div class="stat-big">${money(d.payroll.balance)}<small>${esc(d.payroll.currency)}</small></div>
          <span class="badge-dark">↑ ${d.payroll.deltaPct.toString().replace('.', ',')}% Increased from last month</span>
          <p style="font-size:12px;margin-top:16px">(vs. $${money(d.payroll.prevMonth)} in ${esc(d.payroll.prevLabel)})</p>
        </div>
        <div>${sparkline(d.payroll.weekly)}<div style="text-align:right">${PAYPAL}</div></div>
      </div>
    </div>
    <div class="card">
      <div class="row-head"><h2>Pending Approvals</h2><button class="arrow-btn" id="approveBtn" aria-label="Open">${icon('arrow')}</button></div>
      <div class="stat-big" style="font-size:52px">${money(d.pendingApprovals.amount, 2)}<small>${esc(d.pendingApprovals.currency)}</small></div>
      <div class="card-foot"><span class="muted-lg">Total Pending</span>${PAYPAL}</div>
    </div>
    <div class="card">
      <div class="row-head"><h2>Available Budget</h2><button class="arrow-btn" aria-label="Open">${icon('arrow')}</button></div>
      <div class="stat-big" style="font-size:52px">${money(d.budget.amount, 2)}<small>${esc(d.budget.currency)}</small></div>
      <div class="card-foot"><span class="muted-lg">Total Pending</span>
        <div class="round-actions">
          <div><button data-b="add" aria-label="Add">${icon('plus')}</button>$${money(s1, 2)}</div>
          <div><button data-b="coins" aria-label="Allocate">${icon('coins')}</button>$${money(s2, 2)}</div>
          <div><button data-b="up" aria-label="Transfer">${icon('upload')}</button>$${money(s3, 2)}</div>
        </div>
      </div>
    </div>
  </div>

  <div class="grid g-mid-acc" style="grid-template-columns:1fr 2.3fr 1.1fr 1.1fr; margin-top:22px">
    <div class="stack">
      <div class="card alert-box">
        <h2 style="text-align:left">AI Risk Alerts</h2>${icon('warn')}
        <b>${d.risk.anomalies} Anomalies</b><span style="font-size:15px;font-weight:500">High risk flagged by AI</span>
        <a id="reviewIssues">Review Issues</a>
      </div>
      <div class="card">
        <h2>Compliance Status</h2>
        <div class="donut-wrap">${donut(d.compliance.percent)}
          <ul class="donut-list">${d.compliance.items.map((i) => `<li>${esc(i)}</li>`).join('')}</ul></div>
        <a class="link" id="reviewDocs">Review Documents</a>
      </div>
    </div>
    <div class="card cash">
      <div class="row-head">
        <h2>Cash Flow Analytics</h2>
        <div class="cf-legend"><span>Inflow</span><span class="f2">Outflow</span><span class="f3">Forecast</span></div>
      </div>
      ${cashFlow(d.cashFlow)}
    </div>
    <div class="card">
      <h2>Recent Transactions</h2>
      <div class="scroll-list">${d.transactions.map(txRow).join('')}</div>
      <button class="more" aria-label="More" id="moreTx">${icon('dchev')}</button>
    </div>
    <div class="card">
      <h2>By Department</h2>
      ${d.departments.map((x) => `<div class="dept"><div class="dn">${esc(x.name)}:</div><div class="dv">$${money(x.amount)}</div>
        <div class="bar"><i style="width:${Math.round((x.amount / maxDept) * 100)}%"></i></div></div>`).join('')}
      <button class="more" aria-label="More" id="moreDept">${icon('dchev')}</button>
    </div>
  </div>

  <div class="grid g-bot-acc" style="grid-template-columns:1fr 1fr; margin-top:22px">
    <div class="card">
      <div class="row-head"><h2>Exchange &amp; Fees Savings</h2><span class="tag">+${d.savings.deltaPct}% vs Sep</span></div>
      <div class="savings">+$${money(d.savings.total, 2)}</div>
      <p class="sub" style="color:var(--accent-2)">Total Saved This Month vs Standard Fees</p>
      <div class="save-bars">${d.savings.weeks.map((h) => `<div style="height:${h}%"></div>`).join('')}</div>
      <div class="save-lbl">${d.savings.weeks.map((_, i) => `<span>WK ${i + 1}</span>`).join('')}</div>
      <p class="footnote">Optimized via PayPal Mass Pay &amp; Smart FX Routing</p>
    </div>
    <div class="card" id="aiCard">${chatHTML({ title: 'AI Finance Chat', role: 'accountant' })}</div>
  </div>`;

  bindChat($('#aiCard'), 'accountant');
  $$('[data-b]').forEach((b) => b.onclick = () => (b.dataset.b === 'add' ? openAddFunds() : openSendPayment()));

  /* ---- раскрываемые виджеты ---- */
  const x = d.details;
  const [cPay, cPend, cBud] = $$('.g-top-acc > .card', view);
  const [cRisk, cComp] = $$('.g-mid-acc > .stack > .card', view);
  const [cCash, cTx, cDept] = $$('.g-mid-acc > .card', view);
  const [cSave] = $$('.g-bot-acc > .card', view);
  const goBtn = (id, label) => `<div class="xfoot"><button class="btn sm" data-go="${id}">${label}</button></div>`;
  const bindGo = (box) => $$('[data-go]', box).forEach((b) => b.onclick = () => { box.closeExpanded(); setTimeout(() => goto(b.dataset.go), 360); });
  const sumBy = (arr, k) => arr.reduce((a, i) => a + i[k], 0);

  const buildPay = () => ({
    title: 'Total Payroll Balance', mount: bindGo,
    html: `<div class="xstats">${xstat('Balance', '$' + money(d.payroll.balance))}${xstat('Change', '+' + d.payroll.deltaPct + '%')}${xstat('September', '$' + money(d.payroll.prevMonth))}${xstat('Next payroll run', '31.10.2026')}</div>
      <div class="xsec">Payroll by week</div>
      ${colsBars(d.payroll.weekly.map((v, i) => ({ m: 'Wk ' + (i + 1), v: v * 1000 })), (v) => '$' + money(v / 1000) + 'k')}
      <div class="xnote"><b>AI note:</b> payroll grew ${d.payroll.deltaPct}% against September. Unpaid salaries for October are tracked on the Tasks page.</div>
      ${goBtn('tasks', 'Open payroll and tasks')}`
  });
  const buildPend = () => ({
    title: 'Pending Approvals', mount: bindGo,
    html: `<div class="xstats">${xstat('Total pending', '$' + money(d.pendingApprovals.amount, 2))}${xstat('Payments', x.pending.length)}</div>
      <div class="xsec">Waiting for approval</div>
      ${x.pending.map((p) => xrow(esc(p.name), 'Requested by ' + esc(p.by) + ' · due ' + esc(p.due), '$' + money(p.amount, 2))).join('')}
      ${goBtn('transaction', 'Open Transactions')}`
  });
  const buildBud = () => ({
    title: 'Available Budget',
    html: `<div class="xstats">${xstat('Available', '$' + money(d.budget.amount, 2))}${xstat('Allocated', '$' + money(sumBy(x.alloc, 'allocated')))}${xstat('Spent', '$' + money(sumBy(x.alloc, 'spent')))}</div>
      <div class="xsec">By department</div>
      ${x.alloc.map((a) => `<div class="alloc"><div class="top"><b>${esc(a.dept)}</b><span>$${money(a.spent)} of $${money(a.allocated)}</span></div>
        <div class="bar lg"><i style="width:${Math.round(a.spent / a.allocated * 100)}%"></i></div></div>`).join('')}`
  });
  const buildRisk = () => ({
    title: 'AI Risk Alerts', mount: bindGo,
    html: `<div class="xstats">${xstat('Flagged', x.anomalies.length)}${xstat('At risk', '$' + money(sumBy(x.anomalies, 'amount'), 2))}</div>
      <div class="xsec">Review these payments</div>
      ${x.anomalies.map((a) => xrow('<b>' + esc(a.title) + '</b>', esc(a.detail), '$' + money(a.amount, 2) + ' <span class="badge failed">' + esc(a.level) + '</span>')).join('')}
      ${goBtn('transaction', 'Open Transactions')}`
  });
  const buildComp = () => ({
    title: 'Compliance Status',
    html: `<div class="xstats">${xstat('Verified', d.compliance.percent + '%')}${xstat('Open items', x.docs.filter((o) => !o.ok).length)}</div>
      <div class="xsec">Documents</div>
      ${x.docs.map((o) => xrow(esc(o.name), '', `${esc(o.status)} <span class="badge ${o.ok ? 'paid' : 'unpaid'}">${o.ok ? 'OK' : 'Action needed'}</span>`)).join('')}`
  });
  const buildCash = () => ({
    title: 'Cash Flow Analytics',
    html: `<div class="xsec" style="margin-top:8px">Weekly inflow and outflow</div>
      ${d.cashFlow.bars.map((b, i) => xrow('Week ' + (i + 1), 'Net $' + money((b.in - b.out) * 1000), `<span class="amt-in">+$${money(b.in * 1000)}</span> / −$${money(b.out * 1000)}`)).join('')}
      <div class="xnote"><b>AI note:</b> inflow is higher than outflow in every week. The forecast line dips in week 2 of November because of the tax payment on 15.11.</div>`
  });
  const buildTx = () => ({
    title: 'Recent Transactions', mount: bindGo,
    html: `<div class="xsec" style="margin-top:8px">Latest PayPal payments</div>
      ${d.transactions.map((t) => xrow(esc(t.name), esc(t.date) + ' · USD', '$' + money(t.amount, 2) + ' ' + badge(t.status))).join('')}
      ${goBtn('transaction', 'Open Transactions')}`
  });
  const buildDept = () => ({
    title: 'By Department',
    html: `<div class="xstats">${xstat('Departments', x.alloc.length)}${xstat('Employees', sumBy(x.alloc, 'heads'))}${xstat('Total spent', '$' + money(sumBy(x.alloc, 'spent')))}</div>
      <div class="xsec">Spending against budget</div>
      ${x.alloc.map((a) => `<div class="alloc"><div class="top"><b>${esc(a.dept)} <span>· ${a.heads} people</span></b><span>$${money(a.spent)} / $${money(a.allocated)}</span></div>
        <div class="bar lg"><i style="width:${Math.round(a.spent / a.allocated * 100)}%"></i></div></div>`).join('')}`
  });
  const buildSave = () => ({
    title: 'Exchange & Fees Savings',
    html: `<div class="xstats">${xstat('Saved this month', '$' + money(d.savings.total, 2))}${xstat('Against September', '+' + d.savings.deltaPct + '%')}</div>
      <div class="xsec">Where the savings come from</div>
      ${x.savings.map((s) => xrow(esc(s.label), '', '$' + money(s.amount, 2))).join('')}
      <div class="xnote">Optimized via PayPal Mass Pay and Smart FX routing.</div>`
  });
  makeExpandable(cPay, buildPay); makeExpandable(cPend, buildPend); makeExpandable(cBud, buildBud);
  makeExpandable(cRisk, buildRisk); makeExpandable(cComp, buildComp); makeExpandable(cCash, buildCash);
  makeExpandable(cTx, buildTx); makeExpandable(cDept, buildDept); makeExpandable(cSave, buildSave);
  $('#reviewIssues').onclick = () => expandCard(cRisk, buildRisk());
  $('#reviewDocs').onclick = () => expandCard(cComp, buildComp());
  $('#moreTx').onclick = () => expandCard(cTx, buildTx());
  $('#moreDept').onclick = () => expandCard(cDept, buildDept());
}

/* ====================== Finance ====================== */
const goto = (id) => { const b = $(`#nav [data-view="${id}"]`); if (b) b.click(); };
const xrow = (a, sub, v) => `<div class="xrow"><div>${a}${sub ? `<div class="s">${sub}</div>` : ''}</div><div class="v">${v}</div></div>`;
const xstat = (l, v) => `<div class="xstat"><span>${esc(l)}</span><b>${v}</b></div>`;

function openAddFunds() {
  openModal({
    title: 'Add funds', sub: 'Пополнение PayPal-баланса с банковского счёта',
    body: '<div class="field"><label for="afAmt">Amount, USD</label><input id="afAmt" type="number" min="1" step="0.01" placeholder="10000"></div>',
    actions: [{ label: 'Cancel', ghost: true }, {
      label: 'Add funds', onClick: async (ov) => {
        const a = +$('#afAmt', ov).value;
        if (!(a > 0)) { toast('Введите сумму'); return false; }
        await API.addFunds(a); toast('Пополнение на $' + money(a, 2) + ' запрошено');
      }
    }]
  });
}

async function renderFinance(_id, view) {
  view.innerHTML = '<p class="sub">Загрузка…</p>';
  const f = await API.finance();
  const spent = f.alloc.reduce((a, i) => a + i.spent, 0);
  view.innerHTML = `
  <div class="page-head">
    <div><h1>Finance</h1><p class="pp">Company funds on ${PAYPAL}</p></div>
    <div class="head-actions"><button class="btn sm paypal-btn" id="fAdd">Add funds</button><button class="btn sm line" id="fSend">Send payment</button></div>
  </div>
  <div class="kpis">
    <div class="card kpi"><div class="lbl">PayPal balance</div><div class="val">${money(f.paypalBalance)}<small>USD</small></div><div class="hint up">Ready to use</div></div>
    <div class="card kpi"><div class="lbl">Available budget</div><div class="val">${money(f.available)}<small>USD</small></div><div class="hint">After reserved payments</div></div>
    <div class="card kpi"><div class="lbl">Reserved</div><div class="val">${money(f.reserved)}<small>USD</small></div><div class="hint">Pending approvals and payroll</div></div>
    <div class="card kpi"><div class="lbl">Spent this month</div><div class="val">${money(spent)}<small>USD</small></div><div class="hint">All departments</div></div>
  </div>
  <div class="split">
    <div class="stack">
      <div class="card"><h2>Payroll by month</h2>${colsBars(f.months, (v) => '$' + money(v / 1000) + 'k')}</div>
      <div class="card"><h2>Budget by department</h2>
        ${f.alloc.map((a) => `<div class="alloc"><div class="top"><b>${esc(a.dept)}</b><span>$${money(a.spent)} of $${money(a.allocated)}</span></div>
          <div class="bar lg"><i style="width:${Math.round(a.spent / a.allocated * 100)}%"></i></div></div>`).join('')}</div>
    </div>
    <div class="stack">
      <div class="card"><h2>Upcoming payments</h2>
        ${f.upcoming.map((u) => xrow(esc(u.label), esc(u.date) + ' · ' + esc(u.kind), '$' + money(u.amount))).join('')}</div>
      <div class="card paybox"><h2>Funding source</h2>
        <div class="pay-acc">${PAYPAL}<b>${esc(f.bank)}</b><span>Verified</span></div>
        <button class="btn sm" id="fAdd2">Add funds</button></div>
    </div>
  </div>`;
  $('#fAdd').onclick = $('#fAdd2').onclick = openAddFunds;
  $('#fSend').onclick = openSendPayment;
}

/* ====================== Tasks: кто сколько сделал и сколько выплатить ====================== */
async function renderAccTasks(_id, view) {
  view.innerHTML = '<p class="sub">Загрузка…</p>';
  const [t, sum] = await Promise.all([API.team(), API.aiSummary('accountant')]);
  const E = t.employees;
  const unpaid = () => E.filter((e) => e.status !== 'Paid');
  const total = (arr, k) => arr.reduce((a, e) => a + e[k], 0);

  function paint() {
    const due = total(unpaid(), 'salary'), paid = total(E.filter((e) => e.status === 'Paid'), 'salary');
    view.innerHTML = `
    <div class="page-head">
      <div><h1>Tasks and payroll</h1><p>${esc(t.period)} · who did what and how much to pay via ${PAYPAL}</p></div>
      <div class="head-actions"><button class="btn sm paypal-btn" id="payAll" ${unpaid().length ? '' : 'disabled'}>Pay all unpaid ($${money(due)})</button></div>
    </div>
    <div class="kpis">
      <div class="card kpi"><div class="lbl">Tasks completed</div><div class="val">${total(E, 'tasksDone')}<small>of ${total(E, 'tasksTotal')}</small></div><div class="hint up">${Math.round(total(E, 'tasksDone') / total(E, 'tasksTotal') * 100)}% done</div></div>
      <div class="card kpi"><div class="lbl">Projects ended</div><div class="val">${t.projects.length}</div><div class="hint">This month</div></div>
      <div class="card kpi"><div class="lbl">Salaries to pay</div><div class="val">${money(due)}<small>USD</small></div><div class="hint">${unpaid().length} ${unpaid().length === 1 ? 'employee' : 'employees'}</div></div>
      <div class="card kpi"><div class="lbl">Already paid</div><div class="val">${money(paid)}<small>USD</small></div><div class="hint up">${E.length - unpaid().length} ${E.length - unpaid().length === 1 ? 'employee' : 'employees'}</div></div>
    </div>
    <div class="split eq">
      <div class="card"><h2>Employees</h2>
        <div class="tbl-wrap"><table class="tbl">
          <thead><tr><th>Employee</th><th>Tasks</th><th class="num">Projects</th><th class="num">Hours</th><th class="num">Salary</th><th>Status</th><th></th></tr></thead>
          <tbody>${E.map((e) => `<tr class="click" data-id="${e.id}">
            <td><div class="who"><div class="avatar av-${(e.id % 5) + 1}">${esc(initials(e.name))}</div><div><b>${esc(e.name)}</b><span>${esc(e.dept)}</span></div></div></td>
            <td style="min-width:130px"><div style="font-size:13px;margin-bottom:6px">${e.tasksDone} / ${e.tasksTotal}</div><div class="bar"><i style="width:${Math.round(e.tasksDone / e.tasksTotal * 100)}%"></i></div></td>
            <td class="num">${e.projectsEnded}</td><td class="num">${e.hours} h</td><td class="num"><b>$${money(e.salary)}</b></td>
            <td>${badge(e.status)}</td>
            <td class="num">${e.status === 'Paid' ? '' : `<button class="btn sm" data-pay="${e.id}">Pay</button>`}</td></tr>`).join('')}</tbody>
          <tfoot><tr><td>Total</td><td>${total(E, 'tasksDone')} / ${total(E, 'tasksTotal')}</td><td class="num">${total(E, 'projectsEnded')}</td><td class="num">${total(E, 'hours')} h</td><td class="num">$${money(total(E, 'salary'))}</td><td></td><td></td></tr></tfoot>
        </table></div>
      </div>
      <div class="stack">
        <div class="card ai-sum">${aiSummaryHTML(sum, 'AI Summary')}</div>
        <div class="card list-card"><h2>Projects ended</h2>
          ${t.projects.map((p) => xrow(esc(p.name), 'Lead: ' + esc(p.lead) + ' · ' + esc(p.date), '$' + money(p.budget))).join('')}</div>
      </div>
    </div>`;

    const confirmPay = (ids) => {
      const list = E.filter((e) => ids.includes(e.id));
      const amount = total(list, 'salary');
      openModal({
        title: 'Confirm payment', sub: `PayPal Mass Pay · ${list.length} ${list.length === 1 ? 'recipient' : 'recipients'}`,
        body: `${list.map((e) => xrow(esc(e.name), esc(e.dept), '$' + money(e.salary))).join('')}
          <div class="xrow"><b>Total</b><span class="v">$${money(amount)}</span></div>`,
        actions: [{ label: 'Cancel', ghost: true }, {
          label: 'Pay via PayPal', onClick: async () => {
            const r = await API.payEmployees(ids);
            list.forEach((e) => { e.status = 'Paid'; });
            paint();
            toast(`Выплата $${money(amount)} отправлена (${r.batchId})`);
          }
        }]
      });
    };
    $$('[data-pay]', view).forEach((b) => b.onclick = (ev) => { ev.stopPropagation(); confirmPay([+b.dataset.pay]); });
    const pa = $('#payAll', view); if (pa) pa.onclick = () => confirmPay(unpaid().map((e) => e.id));
    $$('tbody tr.click', view).forEach((r) => r.onclick = (ev) => {
      if (ev.target.closest('button')) return;
      const e = E.find((x) => x.id === +r.dataset.id);
      openModal({
        title: e.name, sub: e.dept + ' · ' + t.period,
        body: `${xrow('Tasks completed', '', e.tasksDone + ' of ' + e.tasksTotal)}${xrow('Projects ended', '', e.projectsEnded)}
          ${xrow('Hours worked', '', e.hours + ' h')}${xrow('Salary to pay', '', '$' + money(e.salary))}${xrow('Status', '', badge(e.status))}`,
        actions: [{ label: 'Close', ghost: true }]
      });
    });
  }
  paint();
}

/* ====================== Reports ====================== */
async function renderAccReports(_id, view) {
  const f = await API.finance();
  return renderReportsPage('accountant', view, { title: 'Payroll by month, USD', items: f.months, fmt: (v) => '$' + money(v / 1000) + 'k' });
}

/* ====================== Router ====================== */
function render(id, view) {
  switch (id) {
    case 'dashboard': return renderDashboard(id, view);
    case 'ai': return renderAIPage('accountant', view);
    case 'transaction': return renderTransactionsPage('accountant', view);
    case 'wallet': return renderFinance(id, view);
    case 'tasks': return renderAccTasks(id, view);
    case 'reports': return renderAccReports(id, view);
    default: return false;
  }
}

mountShell({ role: 'accountant', render });
