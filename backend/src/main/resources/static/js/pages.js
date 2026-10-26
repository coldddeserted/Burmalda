/* Общие страницы для обеих ролей: AI Burmalda, Transactions, Reports и мелкие помощники. */

const PAYPAL = '<span class="paypal"><b>Pay</b>Pal</span>';
const BADGE = { Completed: 'completed', Paid: 'paid', Pending: 'sched', Scheduled: 'sched', Failed: 'failed', Unpaid: 'unpaid' };
const badge = (status) => `<span class="badge ${BADGE[status] || 'todo'}">${esc(status)}</span>`;
const fmtDate = (s) => {
  const [d, t] = String(s).split(' ');
  const [y, m, day] = d.split('-');
  return `${day}.${m}.${y}${t ? ', ' + t : ''}`;
};

async function copyText(text) {
  try { await navigator.clipboard.writeText(text); toast('Скопировано'); }
  catch (e) { toast(text); }
}

function downloadCSV(name, rows) {
  const csv = rows.map((r) => r.map((c) => '"' + String(c).replace(/"/g, '""') + '"').join(',')).join('\n');
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
  a.download = name; document.body.appendChild(a); a.click(); a.remove();
}

function colsBars(items, fmt = (v) => money(v), curIndex = items.length - 1) {
  const max = Math.max(...items.map((i) => i.v));
  return `<div class="cols-bars">${items.map((it, i) => `<div class="${i === curIndex ? 'cur' : ''}">
    <em>${esc(fmt(it.v))}</em><i style="--r:${(it.v / max).toFixed(3)}"></i><span>${esc(it.m)}</span></div>`).join('')}</div>`;
}

function aiSummaryHTML(sum, title = 'AI Summary') {
  return `<h2>${icon('spark')}${esc(title)}</h2>
    <ul>${sum.points.map((p) => `<li class="${esc(p.tone)}">${esc(p.text)}</li>`).join('')}</ul>
    <div class="src">PayPal AI agent · updated ${esc(sum.updated)}</div>`;
}

/* ====================== AI Burmalda ====================== */
async function renderAIPage(role, view) {
  const [sugg, hist] = await Promise.all([API.aiSuggestions(role), API.aiHistory(role)]);
  view.innerHTML = `
  <div class="ai-page">
    <aside class="ai-side">
      <div class="card"><button class="btn sm" id="newChat" style="width:100%">+ New chat</button></div>
      <div class="card grow"><h3>Recent chats</h3>
        <div class="hist" id="hist">${hist.map((h) => `<button type="button">${esc(h)}</button>`).join('')}</div></div>
    </aside>
    <section class="card ai-main">
      <div class="ai-bar">${LOGO_MARK}
        <div><h2>AI Burmalda</h2><div class="sub"><i class="dot"></i>Connected to PayPal AI agent</div></div>
        <span class="pp">Powered by ${PAYPAL}</span></div>
      <div class="ai-thread" id="thread"></div>
      <div class="ai-compose">
        <div class="chips" id="chips">${sugg.map((s) => `<button type="button" class="chip-btn">${esc(s)}</button>`).join('')}</div>
        <div class="composer">
          <textarea id="aiInput" rows="1" placeholder="Ask the PayPal AI agent…" aria-label="Message"></textarea>
          <div class="tools">
            <button type="button" aria-label="Attach">${icon('plus')}</button>
            <button type="button" aria-label="Voice">${icon('mic')}</button>
            <button type="button" class="send" id="aiSend" aria-label="Send">${icon('send')}</button>
          </div>
        </div>
      </div>
    </section>
  </div>`;

  const thread = $('#thread'), input = $('#aiInput');
  const hello = () => {
    thread.innerHTML = `<div class="ai-hello">${LOGO_MARK}<div class="hi">Good to see you!</div>
      <h3>How Can I Assist You With Your ${role === 'accountant' ? 'Finances' : 'Projects'}?</h3>
      <p>Ask about payouts, payments and reports. The PayPal AI agent answers using your company data.</p></div>`;
  };
  hello();
  const time = () => new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  const add = (cls, text) => {
    const h = $('.ai-hello', thread); if (h) h.remove();
    const m = document.createElement('div'); m.className = 'msg ' + cls;
    m.append(document.createTextNode(text));
    const t = document.createElement('small'); t.textContent = time(); m.append(t);
    thread.appendChild(m); thread.scrollTop = thread.scrollHeight; return m;
  };
  async function send(text) {
    const q = (text ?? input.value).trim(); if (!q) return;
    input.value = ''; add('me', q);
    const wait = document.createElement('div'); wait.className = 'msg ai';
    wait.innerHTML = '<span class="typing"><i></i><i></i><i></i></span>';
    thread.appendChild(wait); thread.scrollTop = thread.scrollHeight;
    let reply;
    try { reply = await API.askAI(q, role); } catch (e) { reply = 'Error: ' + e.message; }
    wait.remove(); add('ai', reply);
  }
  $('#aiSend').onclick = () => send();
  input.addEventListener('keydown', (e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); } });
  $$('#chips .chip-btn').forEach((b) => b.onclick = () => send(b.textContent));
  $$('#hist button').forEach((b) => b.onclick = () => {
    $$('#hist button').forEach((x) => x.classList.toggle('on', x === b));
    hello(); send(b.textContent);
  });
  $('#newChat').onclick = () => { $$('#hist button').forEach((x) => x.classList.remove('on')); hello(); input.focus(); };
}

/* ====================== Transactions (только PayPal) ====================== */
async function renderTransactionsPage(role, view) {
  view.innerHTML = '<p class="sub">Загрузка…</p>';
  let data;
  try { data = await API.transactions(role); } catch (e) { view.innerHTML = `<p class="sub">Ошибка: ${esc(e.message)}</p>`; return; }
  const done = data.items.filter((t) => t.status === 'Completed');
  const sum = (arr) => arr.reduce((a, t) => a + t.amount, 0);
  const inSum = sum(done.filter((t) => t.dir === 'in')), outSum = sum(done.filter((t) => t.dir === 'out'));
  const fees = done.reduce((a, t) => a + t.fee, 0);
  const isAcc = role === 'accountant';
  let filter = 'all', q = '';

  view.innerHTML = `
  <div class="page-head">
    <div><h1>Transactions</h1><p class="pp">All payments go through ${PAYPAL}</p></div>
    <div class="head-actions">
      ${isAcc ? '<button class="btn sm paypal-btn" id="sendPay">Send payment</button>' : ''}
      <button class="btn sm line" id="exportCsv">${icon('download').replace('<svg', '<svg width="16" height="16"')} Export CSV</button>
    </div>
  </div>
  <div class="kpis">
    <div class="card kpi"><div class="lbl">PayPal balance</div><div class="val">${money(data.balance, 2)}<small>USD</small></div><div class="hint">Available now</div></div>
    <div class="card kpi"><div class="lbl">${isAcc ? 'Money in' : 'Received'}</div><div class="val">${money(inSum, 2)}<small>USD</small></div><div class="hint up">Completed payments</div></div>
    <div class="card kpi"><div class="lbl">${isAcc ? 'Money out' : 'Withdrawn'}</div><div class="val">${money(outSum, 2)}<small>USD</small></div><div class="hint">Completed payments</div></div>
    <div class="card kpi"><div class="lbl">PayPal fees</div><div class="val">${money(fees, 2)}<small>USD</small></div><div class="hint">Total on this list</div></div>
  </div>
  <div class="card">
    <div class="toolbar">
      <div class="seg" id="seg" role="tablist">
        <button class="on" data-f="all">All</button><button data-f="in">Incoming</button><button data-f="out">Outgoing</button><button data-f="pending">Pending</button>
      </div>
      <label class="search">${icon('search')}<input id="txQ" placeholder="Search by name, note or ID" autocomplete="off"></label>
    </div>
    <div class="tbl-wrap"><table class="tbl">
      <thead><tr><th>Date</th><th>Name</th><th>PayPal ID</th><th class="num">Amount</th><th class="num">Fee</th><th>Status</th></tr></thead>
      <tbody id="txBody"></tbody></table></div>
    <div class="empty" id="txEmpty" hidden>No transactions match your filters.</div>
  </div>`;

  const rows = () => data.items.filter((t) => {
    if (filter === 'in' && t.dir !== 'in') return false;
    if (filter === 'out' && t.dir !== 'out') return false;
    if (filter === 'pending' && t.status !== 'Pending') return false;
    const s = q.toLowerCase();
    return !s || (t.name + ' ' + t.note + ' ' + t.id).toLowerCase().includes(s);
  });
  function paint() {
    const list = rows();
    $('#txBody').innerHTML = list.map((t) => `<tr class="click" data-id="${esc(t.id)}" tabindex="0">
      <td>${esc(fmtDate(t.date))}</td>
      <td><div class="who"><div><b>${esc(t.name)}</b><span>${esc(t.note)}</span></div></div></td>
      <td style="font-size:12px;color:var(--muted)">${esc(t.id)}</td>
      <td class="num ${t.dir === 'in' ? 'amt-in' : 'amt-out'}">${t.dir === 'in' ? '+' : '−'}$${money(t.amount, 2)}</td>
      <td class="num">${t.fee ? '$' + money(t.fee, 2) : '—'}</td>
      <td>${badge(t.status)}</td></tr>`).join('');
    $('#txEmpty').hidden = list.length > 0;
  }
  paint();
  $$('#seg button').forEach((b) => b.onclick = () => {
    filter = b.dataset.f; $$('#seg button').forEach((x) => x.classList.toggle('on', x === b)); paint();
  });
  $('#txQ').addEventListener('input', (e) => { q = e.target.value.trim(); paint(); });

  const openTx = (id) => {
    const t = data.items.find((x) => x.id === id); if (!t) return;
    const net = t.dir === 'in' ? t.amount - t.fee : t.amount + t.fee;
    openModal({
      title: t.name, sub: t.note,
      body: `<div class="xrow"><span>Amount</span><span class="v ${t.dir === 'in' ? 'amt-in' : ''}">${t.dir === 'in' ? '+' : '−'}$${money(t.amount, 2)} USD</span></div>
        <div class="xrow"><span>PayPal fee</span><span class="v">${t.fee ? '$' + money(t.fee, 2) : '—'}</span></div>
        <div class="xrow"><span>${t.dir === 'in' ? 'Net received' : 'Total charged'}</span><span class="v">$${money(net, 2)}</span></div>
        <div class="xrow"><span>Date</span><span class="v">${esc(fmtDate(t.date))}</span></div>
        <div class="xrow"><span>Status</span><span class="v">${badge(t.status)}</span></div>
        <div class="xrow"><span>PayPal transaction ID</span><span class="v">${esc(t.id)}</span></div>`,
      actions: [
        { label: 'Copy ID', ghost: true, onClick: () => { copyText(t.id); return false; } },
        { label: 'Close' }
      ]
    });
  };
  $('#txBody').addEventListener('click', (e) => { const r = e.target.closest('tr'); if (r) openTx(r.dataset.id); });
  $('#txBody').addEventListener('keydown', (e) => { if (e.key === 'Enter') { const r = e.target.closest('tr'); if (r) openTx(r.dataset.id); } });

  $('#exportCsv').onclick = () => {
    downloadCSV('paypal-transactions.csv', [['Date', 'Name', 'Note', 'PayPal ID', 'Direction', 'Amount', 'Fee', 'Status'],
      ...rows().map((t) => [t.date, t.name, t.note, t.id, t.dir, t.amount, t.fee, t.status])]);
    toast('CSV сформирован');
  };
  const sp = $('#sendPay');
  if (sp) sp.onclick = () => openSendPayment();
}

function openSendPayment() {
  openModal({
    title: 'Send payment', sub: 'Платёж будет отправлен через PayPal',
    body: `<div class="field"><label for="spTo">Recipient (PayPal email)</label><input id="spTo" type="email" placeholder="name@company.com"></div>
      <div class="field"><label for="spAmt">Amount, USD</label><input id="spAmt" type="number" min="1" step="0.01" placeholder="100.00"></div>
      <div class="field"><label for="spNote">Note</label><input id="spNote" placeholder="Invoice 2041"></div>`,
    actions: [{ label: 'Cancel', ghost: true }, {
      label: 'Send', onClick: async (ov) => {
        const to = $('#spTo', ov).value.trim(), amount = +$('#spAmt', ov).value;
        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(to)) { toast('Введите корректный email'); return false; }
        if (!(amount > 0)) { toast('Введите сумму'); return false; }
        await API.sendPayment({ to, amount, note: $('#spNote', ov).value.trim() });
        toast('Платёж на $' + money(amount, 2) + ' отправлен');
      }
    }]
  });
}

/* ====================== Reports ====================== */
async function renderReportsPage(role, view, chart) {
  view.innerHTML = '<p class="sub">Загрузка…</p>';
  const [list, sum] = await Promise.all([API.reports(role), API.aiSummary(role)]);
  let period = 'Month';
  view.innerHTML = `
  <div class="page-head">
    <div><h1>Reports</h1><p>${role === 'accountant' ? 'Company reports for accounting and filing' : 'Your personal reports'}</p></div>
    <div class="seg" id="periodSeg"><button class="on" data-p="Month">Month</button><button data-p="Quarter">Quarter</button><button data-p="Year">Year</button></div>
  </div>
  <div class="split">
    <div class="stack">
      <div class="card"><h2>${esc(chart.title)}</h2>${colsBars(chart.items, chart.fmt)}</div>
      <div class="rep-grid">${list.map((r) => `<div class="card rep">
        <div class="ri">${icon('file')}</div><h3>${esc(r.name)}</h3><p>${esc(r.desc)}</p>
        <div class="row">${r.formats.map((f) => `<button class="btn sm line" data-r="${esc(r.id)}" data-f="${esc(f)}">${esc(f)}</button>`).join('')}</div></div>`).join('')}</div>
    </div>
    <div class="card ai-sum">${aiSummaryHTML(sum, 'AI Summary')}</div>
  </div>`;
  $$('#periodSeg button').forEach((b) => b.onclick = () => {
    period = b.dataset.p; $$('#periodSeg button').forEach((x) => x.classList.toggle('on', x === b));
  });
  $$('[data-r]').forEach((b) => b.onclick = async () => {
    const r = list.find((x) => x.id === b.dataset.r);
    b.disabled = true;
    await API.generateReport(r.id, b.dataset.f, period);
    b.disabled = false;
    toast(`${r.name} (${b.dataset.f}) за период «${period}» готов`);
  });
}
