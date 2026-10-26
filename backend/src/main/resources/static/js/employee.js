/* Кабинет сотрудника (инженер и т.п.) */
let timerHandle = null;
const goto = (id) => { const b = $(`#nav [data-view="${id}"]`); if (b) b.click(); };
const DAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

function weekChart(data) {
  return `<div class="week">${data.map((d) => `
    <div class="day">
      <div class="pillbar ${d.type}" style="height:${Math.round(d.value * 1.35)}px">${d.tip ? `<span class="tip">${esc(d.tip)}</span>` : ''}</div>
      <span class="lbl">${esc(d.day)}</span>
    </div>`).join('')}</div>`;
}

function gauge(p) {
  const cx = 125, cy = 115, r = 88, w = 26;
  const total = p.completed + p.inProgress + p.pending || 1;
  const a1 = 180 + (p.completed / total) * 180;
  const a2 = a1 + (p.inProgress / total) * 180;
  return `<div class="gauge-wrap">
    <div class="gauge">
      <svg viewBox="0 0 250 130" width="100%" preserveAspectRatio="xMidYMid meet">
        <defs><pattern id="hatch" width="5" height="5" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
          <line x1="0" y1="0" x2="0" y2="5" style="stroke:var(--text)" stroke-width="1.3"/></pattern></defs>
        <path d="${arcPath(cx, cy, r, 180, a1)}" fill="none" stroke="var(--accent)" stroke-width="${w}"/>
        <path d="${arcPath(cx, cy, r, a1, a2)}" fill="none" stroke="#5f6368" stroke-width="${w}"/>
        <path d="${arcPath(cx, cy, r, a2, 360)}" fill="none" stroke="url(#hatch)" stroke-width="${w}"/>
      </svg>
      <div class="center"><b>${p.completed}%</b><span>Project Ended</span></div>
    </div>
    <div class="legend">
      <span><i class="l1"></i>completed</span><span><i class="l2"></i>in progress</span><span><i class="hatch"></i>Pending</span>
    </div>
  </div>`;
}

const STATUS = { progress: 'In Progress', pending: 'Pending', completed: 'Completed' };
function memberRow(m, i) {
  return `<div class="member">
    <div class="avatar sm av-${(i % 5) + 1}">${esc(initials(m.name))}</div>
    <div class="mi"><div class="mn">${esc(m.name)}</div><div class="mt">Working on <b>${esc(m.task)}</b></div></div>
    <span class="badge ${esc(m.status)}">${esc(STATUS[m.status] || m.status)}</span>
  </div>`;
}

function fmtTime(s) {
  const h = String(Math.floor(s / 3600)).padStart(2, '0');
  const m = String(Math.floor((s % 3600) / 60)).padStart(2, '0');
  return `${h}:${m}:${String(s % 60).padStart(2, '0')}`;
}

function setupTracker(root, startSeconds) {
  let sec = startSeconds, running = true;
  const out = $('#timeOut', root), pp = $('#ttPause', root);
  const paint = () => { out.textContent = fmtTime(sec); pp.innerHTML = running ? icon('pause') : icon('play'); };
  clearInterval(timerHandle);
  timerHandle = setInterval(() => { if (running) { sec++; out.textContent = fmtTime(sec); } }, 1000);
  onLeave(() => clearInterval(timerHandle));
  pp.onclick = () => { running = !running; paint(); };
  $('#ttStop', root).onclick = async () => {
    running = false; paint();
    await API.logTime(sec);
    toast('Время сохранено: ' + fmtTime(sec));
    sec = 0; paint();
  };
  paint();
}

function openWithdraw(available, currency) {
  openModal({
    title: 'Withdraw to PayPal', sub: `Доступно: ${money(available)} ${currency}`,
    body: '<div class="field"><label for="wAmt">Сумма</label><input id="wAmt" type="number" min="1" placeholder="100"></div>',
    actions: [{ label: 'Cancel', ghost: true }, {
      label: 'Withdraw', onClick: async (ov) => {
        const a = +$('#wAmt', ov).value;
        if (!(a > 0) || a > available) { toast('Введите корректную сумму'); return false; }
        await API.withdraw(a); toast('Запрос на вывод ' + money(a) + ' ' + currency + ' отправлен');
      }
    }]
  });
}

/* строка «название / подпись / значение» для раскрытых виджетов */
const xrow = (a, sub, v) => `<div class="xrow"><div>${a}${sub ? `<div class="s">${sub}</div>` : ''}</div><div class="v">${v}</div></div>`;
const xstat = (l, v) => `<div class="xstat"><span>${esc(l)}</span><b>${v}</b></div>`;

/* ====================== Dashboard ====================== */
async function renderDashboard(_id, view) {
  view.innerHTML = '<p class="sub">Загрузка…</p>';
  let d;
  try { d = await API.employeeDashboard(); } catch (e) { view.innerHTML = `<p class="sub">Ошибка: ${esc(e.message)}</p>`; return; }
  const x = d.details;

  view.innerHTML = `
  <div class="grid g-top-emp" style="grid-template-columns:1.05fr 1fr 1fr 1fr">
    <div class="card dark">
      <h2 style="font-weight:600">Total Earned</h2>
      <div class="stat-big">${money(d.totalEarned.amount)}<small>${esc(d.totalEarned.currency)}</small></div>
      <div class="pill-btns"><button class="pill" id="withdrawBtn">Withdraw</button><button class="pill" id="historyBtn">History</button></div>
    </div>
    <div class="card">
      <div class="row-head"><h2>Ended Projects</h2><button class="arrow-btn" aria-label="Open">${icon('arrow')}</button></div>
      <div class="stat-big">${d.endedProjects.count}</div>
      <div class="stat-note"><span class="chip">${d.endedProjects.delta}▲</span>Increased from last month</div>
    </div>
    <div class="card">
      <div class="row-head"><h2>Running Projects</h2><button class="arrow-btn" aria-label="Open">${icon('arrow')}</button></div>
      <div class="stat-big">${d.runningProjects.count}</div>
      <div class="stat-note"><span class="chip">${d.runningProjects.delta}▲</span>Increased from last month</div>
    </div>
    <div class="card">
      <div class="row-head"><h2>Running Projects</h2><button class="arrow-btn" aria-label="Open">${icon('arrow')}</button></div>
      <div class="stat-big">${d.discussProjects.count}</div>
      <div class="stat-note">${esc(d.discussProjects.label)}</div>
    </div>
  </div>

  <div class="grid g-main-emp" style="grid-template-columns:1.45fr 1fr; margin-top:22px">
    <div class="stack">
      <div class="card"><h2>Project Analytics</h2>${weekChart(d.analytics)}</div>
      <div class="card">
        <div class="row-head"><h2>Team Collaboration</h2><button class="btn-outline" id="addMemberBtn">+ Add Member</button></div>
        <div id="team" style="margin-top:6px">${d.team.map(memberRow).join('')}</div>
      </div>
    </div>
    <div class="stack">
      <div class="card" id="aiCard">${chatHTML({ title: 'AI Assistant', role: 'employee' })}</div>
      <div class="grid sub-grid" style="grid-template-columns:1fr 1fr">
        <div class="card"><h2>Project Progress</h2>${gauge(d.progress)}</div>
        <div class="card dark tracker" id="trackerCard">
          <h2>Time Tracker</h2>
          <div class="time" id="timeOut">00:00:00</div>
          <div class="ctrls"><button id="ttPause" aria-label="Pause"></button><button id="ttStop" aria-label="Stop"></button></div>
        </div>
      </div>
    </div>
  </div>`;

  $('#ttStop').innerHTML = icon('stop');
  bindChat($('#aiCard'), 'employee');
  setupTracker($('#trackerCard'), d.timer.seconds);

  $('#withdrawBtn').onclick = () => openWithdraw(d.totalEarned.amount, d.totalEarned.currency);
  $('#historyBtn').onclick = () => goto('transaction');

  $('#addMemberBtn').onclick = () => openModal({
    title: 'Add Member', sub: 'Добавить участника в команду',
    body: `<div class="field"><label for="mName">Имя</label><input id="mName" placeholder="Name Surname"></div>
           <div class="field"><label for="mEmail">Email</label><input id="mEmail" type="email" placeholder="name@company.com"></div>
           <div class="field"><label for="mTask">Задача</label><input id="mTask" placeholder="Task title"></div>`,
    actions: [{ label: 'Cancel', ghost: true }, {
      label: 'Add', onClick: async (ov) => {
        const name = $('#mName', ov).value.trim();
        if (!name) { toast('Введите имя'); return false; }
        const m = await API.addMember({ name, email: $('#mEmail', ov).value.trim(), task: $('#mTask', ov).value.trim() });
        const list = $('#team');
        list.insertAdjacentHTML('beforeend', memberRow(m, list.children.length));
      }
    }]
  });

  /* ---- раскрываемые виджеты ---- */
  const [cEarn, cEnd, cRun, cDisc] = $$('.g-top-emp > .card', view);
  const [cAn, cTeam] = $$('.g-main-emp > .stack:first-child > .card', view);
  const [cProg, cTrack] = $$('.sub-grid > .card', view);
  const goBtn = (id, label) => `<div class="xfoot"><button class="btn sm" data-go="${id}">${label}</button></div>`;
  const bindGo = (box) => $$('[data-go]', box).forEach((b) => b.onclick = () => { box.closeExpanded(); setTimeout(() => goto(b.dataset.go), 360); });

  makeExpandable(cEarn, () => ({
    title: 'Total Earned', mount: bindGo,
    html: `<div class="xstats">${xstat('Balance', '$' + money(d.totalEarned.amount))}${xstat('Next payday', '31.10.2026')}${xstat('Scheduled', '$3,200')}</div>
      <div class="xsec">Payouts via PayPal</div>
      ${x.payouts.map((p) => xrow(esc(p.label), esc(p.date), `$${money(p.amount)} ${badge(p.status)}`)).join('')}
      ${goBtn('wallet', 'Open My wallet')}`
  }));
  makeExpandable(cEnd, () => ({
    title: 'Ended Projects',
    html: `<div class="xstats">${xstat('Finished', d.endedProjects.count)}${xstat('More than last month', '+' + d.endedProjects.delta)}${xstat('Paid for them', '$' + money(x.ended.reduce((a, p) => a + p.pay, 0)))}</div>
      <div class="xsec">Finished projects</div>
      ${x.ended.map((p) => xrow(esc(p.name), 'Finished ' + esc(p.date), '$' + money(p.pay))).join('')}`
  }));
  makeExpandable(cRun, () => ({
    title: 'Running Projects',
    html: `<div class="xstats">${xstat('Running', d.runningProjects.count)}${xstat('Average progress', Math.round(x.running.reduce((a, p) => a + p.progress, 0) / x.running.length) + '%')}${xstat('Nearest deadline', esc(x.running[0].due))}</div>
      <div class="xsec">In progress</div>
      ${x.running.map((p) => `<div class="xrow"><div>${esc(p.name)}<div class="s">Due ${esc(p.due)}</div></div><div class="v">${p.progress}%</div>
        <div class="full"><div class="bar lg"><i style="width:${p.progress}%"></i></div></div></div>`).join('')}`
  }));
  makeExpandable(cDisc, () => ({
    title: 'On Discuss',
    html: `<div class="xstats">${xstat('Projects', d.discussProjects.count)}</div>
      <div class="xsec">Waiting for a decision</div>
      ${x.discuss.map((p) => xrow(esc(p.name), esc(p.note), badge('Pending'))).join('')}`
  }));
  makeExpandable(cAn, () => {
    const hrs = d.analytics.map((a) => +(a.value * 0.09).toFixed(1));
    const total = hrs.reduce((a, b) => a + b, 0), best = hrs.indexOf(Math.max(...hrs));
    return {
      title: 'Project Analytics',
      html: `<div class="xstats">${xstat('Hours this week', total.toFixed(1))}${xstat('Average per day', (total / 7).toFixed(1))}${xstat('Longest day', DAYS[best])}</div>
        <div class="xsec">Hours by day</div>
        ${hrs.map((h, i) => `<div class="xrow"><div>${DAYS[i]}<div class="bar lg" style="margin-top:8px"><i style="width:${Math.round(h / 9 * 100)}%"></i></div></div><div class="v">${h.toFixed(1)} h</div></div>`).join('')}`
    };
  });
  makeExpandable(cTeam, () => ({
    title: 'Team Collaboration',
    html: `<div class="xstats">${xstat('Members', $$('#team .member').length)}${xstat('In progress', d.team.filter((m) => m.status === 'progress').length)}${xstat('Completed', d.team.filter((m) => m.status === 'completed').length)}</div>
      <div class="xsec">Everyone</div>${$('#team').innerHTML}`
  }));
  makeExpandable(cProg, () => ({
    title: 'Project Progress',
    html: `<div class="xstats">${xstat('Completed', d.progress.completed + '%')}${xstat('In progress', d.progress.inProgress + '%')}${xstat('Pending', d.progress.pending + '%')}</div>
      <div style="max-width:380px;margin:12px auto 0">${gauge(d.progress)}</div>
      <div class="xnote"><b>AI note:</b> ${d.progress.pending}% of the work has not started. Start with the tasks due this week to move it forward.</div>`
  }));
  makeExpandable(cTrack, () => ({
    title: 'Time Tracker',
    html: `<div class="xstats">${xstat('Tracked today', fmtTime(d.timer.seconds))}${xstat('Sessions', x.sessions.length)}</div>
      <div class="xsec">Today</div>
      ${x.sessions.map((s) => xrow(esc(s.project), 'Started ' + esc(s.from), esc(s.dur))).join('')}`
  }));
}

/* ====================== My wallet ====================== */
async function renderWallet(_id, view) {
  view.innerHTML = '<p class="sub">Загрузка…</p>';
  const w = await API.wallet();
  view.innerHTML = `
  <div class="page-head"><div><h1>My wallet</h1><p class="pp">Your earnings are paid out via ${PAYPAL}</p></div></div>
  <div class="split">
    <div class="stack">
      <div class="card dark wallet-card">
        <div><h2>Available balance</h2>
          <div class="stat-big">${money(w.balance)}<small>${esc(w.currency)}</small></div></div>
        <div class="pill-btns"><button class="pill" id="wdBtn">Withdraw to PayPal</button><button class="pill" id="wdHist">History</button></div>
      </div>
      <div class="card"><h2>Earnings by month</h2>${colsBars(w.months, (v) => '$' + money(v))}</div>
      <div class="card list-card"><h2>Payout history</h2>
        ${w.history.map((p) => xrow(esc(p.label), esc(p.date), `$${money(p.amount)} ${badge(p.status)}`)).join('')}</div>
    </div>
    <div class="stack">
      <div class="card paybox"><h2>PayPal account</h2>
        <div class="pay-acc">${PAYPAL}<b>${esc(w.paypal.email)}</b><span>${w.paypal.verified ? 'Verified' : 'Not verified'}</span></div>
        <button class="btn sm paypal-btn" id="wdBtn2">Withdraw</button></div>
      <div class="card"><h2>Next payday</h2>
        <div class="stat-big" style="font-size:44px">${esc(w.nextPayday)}</div>
        <div class="sub">Scheduled salary: <b style="color:var(--text)">$${money(w.pending)}</b></div></div>
    </div>
  </div>`;
  $('#wdBtn').onclick = $('#wdBtn2').onclick = () => openWithdraw(w.balance, w.currency);
  $('#wdHist').onclick = () => goto('transaction');
}

/* ====================== Tasks ====================== */
async function renderTasks(_id, view) {
  view.innerHTML = '<p class="sub">Загрузка…</p>';
  const tasks = await API.myTasks();
  const cols = [['todo', 'To do'], ['progress', 'In progress'], ['done', 'Done']];
  const next = { todo: 'progress', progress: 'done', done: 'todo' };
  const prioName = { high: 'High', mid: 'Medium', low: 'Low' };

  function paint() {
    view.innerHTML = `
    <div class="page-head"><div><h1>Tasks</h1><p>Нажмите на задачу, чтобы перенести её в следующую колонку</p></div></div>
    <div class="kpis" style="grid-template-columns:repeat(3,1fr)">
      ${cols.map(([k, n]) => `<div class="card kpi"><div class="lbl">${n}</div><div class="val">${tasks.filter((t) => t.status === k).length}</div></div>`).join('')}
    </div>
    <div class="board">${cols.map(([k, n]) => {
      const list = tasks.filter((t) => t.status === k);
      return `<div class="card"><div class="col-head"><h2>${n}</h2><span class="count">${list.length}</span></div>
        ${list.map((t) => `<button class="task" data-id="${t.id}"><b>${esc(t.title)}</b><span style="font-size:12px;color:var(--muted)">${esc(t.project)}</span>
          <div class="meta"><span>Due ${esc(t.due)}</span><span class="prio ${esc(t.prio)}">${prioName[t.prio]}</span></div></button>`).join('') || '<div class="col-empty">No tasks here</div>'}</div>`;
    }).join('')}</div>`;
    $$('.task', view).forEach((b) => b.onclick = async () => {
      const t = tasks.find((x) => x.id === +b.dataset.id);
      t.status = next[t.status];
      await API.updateTask(t.id, t.status);
      paint();
      toast(`«${t.title}» → ${cols.find((c) => c[0] === t.status)[1]}`);
    });
  }
  paint();
}

/* ====================== Reports ====================== */
async function renderEmpReports(_id, view) {
  const w = await API.wallet();
  return renderReportsPage('employee', view, { title: 'Earnings by month, USD', items: w.months, fmt: (v) => '$' + money(v) });
}

/* ====================== Router ====================== */
function render(id, view) {
  switch (id) {
    case 'dashboard': return renderDashboard(id, view);
    case 'ai': return renderAIPage('employee', view);
    case 'transaction': return renderTransactionsPage('employee', view);
    case 'wallet': return renderWallet(id, view);
    case 'tasks': return renderTasks(id, view);
    case 'reports': return renderEmpReports(id, view);
    default: return false;
  }
}

mountShell({ role: 'employee', render });
