/* Общие функции: иконки, модалки, тосты, каркас (сайдбар + шапка), чат. */

const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const money = (n, d = 0) => Number(n).toLocaleString('en-US', { minimumFractionDigits: d, maximumFractionDigits: d });
const initials = (name) => String(name || '?').split(/\s+/).map((w) => w[0]).slice(0, 2).join('').toUpperCase();

const I = (path, extra = '') => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" ${extra}>${path}</svg>`;
const ICONS = {
  home: I('<path d="M3 11l9-8 9 8"/><path d="M5 10v10h14V10"/><path d="M10 20v-6h4v6"/>'),
  chart: I('<rect x="3" y="3" width="18" height="18" rx="2"/><path d="M8 17v-5M12 17V8M16 17v-3"/>'),
  list: I('<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M7 9h10M7 13h10M7 17h6"/>'),
  wallet: I('<rect x="3" y="6" width="18" height="13" rx="2"/><path d="M3 10h18"/><circle cx="16.5" cy="14.5" r="1.2"/>'),
  task: I('<rect x="5" y="4" width="14" height="17" rx="2"/><path d="M9 4V3h6v1"/>'),
  report: I('<path d="M12 3a9 9 0 109 9h-9z"/><path d="M15 3.5A9 9 0 0120.5 9H15z"/>'),
  search: I('<circle cx="11" cy="11" r="7"/><path d="M20 20l-4-4"/>'),
  gear: I('<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 00.3 1.8l.1.1a2 2 0 11-2.8 2.8l-.1-.1a1.7 1.7 0 00-1.8-.3 1.7 1.7 0 00-1 1.5V21a2 2 0 11-4 0v-.1a1.7 1.7 0 00-1.1-1.5 1.7 1.7 0 00-1.8.3l-.1.1a2 2 0 11-2.8-2.8l.1-.1a1.7 1.7 0 00.3-1.8 1.7 1.7 0 00-1.5-1H3a2 2 0 110-4h.1a1.7 1.7 0 001.5-1.1 1.7 1.7 0 00-.3-1.8l-.1-.1a2 2 0 112.8-2.8l.1.1a1.7 1.7 0 001.8.3H9a1.7 1.7 0 001-1.5V3a2 2 0 114 0v.1a1.7 1.7 0 001 1.5 1.7 1.7 0 001.8-.3l.1-.1a2 2 0 112.8 2.8l-.1.1a1.7 1.7 0 00-.3 1.8V9a1.7 1.7 0 001.5 1H21a2 2 0 110 4h-.1a1.7 1.7 0 00-1.5 1z"/>'),
  help: I('<circle cx="12" cy="12" r="9.5"/><path d="M9.5 9.5a2.5 2.5 0 114 2c-.9.6-1.5 1.1-1.5 2.2M12 17v.01"/>'),
  menu: I('<path d="M4 6h16M4 12h16M4 18h16"/>'),
  chev: I('<path d="M6 9l6 6 6-6"/>'),
  dchev: I('<path d="M6 6l6 6 6-6M6 13l6 6 6-6"/>'),
  arrow: I('<path d="M7 17L17 7M8 7h9v9"/>', 'stroke-width="1.8"'),
  plus: I('<path d="M12 5v14M5 12h14"/>'),
  mic: I('<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0014 0M12 18v3"/>'),
  image: I('<rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="9" cy="10" r="1.6"/><path d="M21 16l-5-5-8 9"/>'),
  send: I('<path d="M4 12l16-8-6 16-2.5-6.5z"/>'),
  pause: '<svg viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="4" height="16" rx="1"/><rect x="14" y="4" width="4" height="16" rx="1"/></svg>',
  play: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M7 4l13 8-13 8z"/></svg>',
  stop: '<svg viewBox="0 0 24 24" fill="currentColor"><rect x="5" y="5" width="14" height="14" rx="3"/></svg>',
  warn: I('<path d="M12 3L2 20h20z"/><path d="M12 10v4.5M12 17.5v.01"/>', 'stroke-width="1.4"'),
  coins: I('<ellipse cx="12" cy="6" rx="7" ry="3"/><path d="M5 6v6c0 1.7 3.1 3 7 3s7-1.3 7-3V6M5 12v6c0 1.7 3.1 3 7 3s7-1.3 7-3v-6"/>'),
  upload: I('<path d="M12 16V5M7 10l5-5 5 5M5 20h14"/>'),
  sun: I('<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>'),
  moon: I('<path d="M20 14.5A8.5 8.5 0 019.5 4 8.5 8.5 0 1020 14.5z"/>'),
  auto: I('<rect x="3" y="4" width="18" height="12" rx="2"/><path d="M8 20h8M12 16v4"/>'),
  logout: I('<path d="M9 4H5a2 2 0 00-2 2v12a2 2 0 002 2h4"/><path d="M16 17l5-5-5-5M21 12H9"/>'),
  spark: I('<path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z"/><path d="M19 16l.7 2 2 .7-2 .7-.7 2-.7-2-2-.7 2-.7z"/>'),
  download: I('<path d="M12 4v11M7 11l5 5 5-5M5 20h14"/>'),
  check: I('<path d="M5 12.5l4.5 4.5L19 7.5"/>'),
  users: I('<circle cx="9" cy="8" r="3.2"/><path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6"/><path d="M16 5a3 3 0 010 6M18 14.3c1.8.8 3 2.6 3 4.7"/>'),
  file: I('<path d="M6 3h8l4 4v14H6z"/><path d="M14 3v4h4M9 13h6M9 17h6"/>'),
  close: I('<path d="M6 6l12 12M18 6L6 18"/>'),
  swap: I('<path d="M4 8h14l-3-3M20 16H6l3 3"/>')
};
const icon = (n) => ICONS[n] || '';

const LOGO = `<svg viewBox="0 0 34 44"><path d="M17 1L33 19H1z" fill="#a9b8d0"/><path d="M17 43L1 25h32z" fill="#3a4450"/></svg>`;
const LOGO_MARK = `<svg viewBox="0 0 34 44" class="mark"><path d="M17 1L33 19H1z" fill="#a9b8d0"/><path d="M17 43L1 25h32z" fill="#3a4450"/></svg>`;

/* ---------- тост ---------- */
function toast(msg) {
  const t = document.createElement('div');
  t.className = 'toast'; t.textContent = msg;
  document.body.appendChild(t);
  setTimeout(() => t.remove(), 2600);
}

/* ---------- модальное окно ---------- */
function openModal({ title, sub = '', body = '', actions = [] }) {
  const ov = document.createElement('div');
  ov.className = 'overlay';
  ov.innerHTML = `<div class="modal" role="dialog" aria-modal="true">
    <h3>${esc(title)}</h3>${sub ? `<p class="sub">${esc(sub)}</p>` : ''}
    <div class="modal-body">${body}</div>
    <div class="modal-actions"></div></div>`;
  const close = () => ov.remove();
  const box = $('.modal-actions', ov);
  actions.forEach((a) => {
    const b = document.createElement('button');
    b.className = 'btn' + (a.ghost ? ' ghost' : '');
    b.textContent = a.label;
    b.onclick = async () => { if (!a.onClick || (await a.onClick(ov)) !== false) close(); };
    box.appendChild(b);
  });
  ov.addEventListener('mousedown', (e) => { if (e.target === ov) close(); });
  document.addEventListener('keydown', function esc_(e) { if (e.key === 'Escape') { close(); document.removeEventListener('keydown', esc_); } });
  document.body.appendChild(ov);
  const first = $('input,select', ov); if (first) first.focus();
  return ov;
}

/* ---------- настройки (выбор темы) ---------- */
function openSettings() {
  const cur = Theme.get();
  const opt = (m, label, ic) => `<button data-mode="${m}" class="${cur === m ? 'on' : ''}">${icon(ic)}${label}</button>`;
  const ov = openModal({
    title: 'Settings', sub: 'Выберите тему оформления',
    body: `<div class="theme-opts">${opt('light', 'Light', 'sun')}${opt('dark', 'Dark', 'moon')}${opt('system', 'System', 'auto')}</div>`,
    actions: [{ label: 'Done', ghost: true }]
  });
  $$('.theme-opts button', ov).forEach((b) => b.onclick = () => {
    Theme.set(b.dataset.mode);
    $$('.theme-opts button', ov).forEach((x) => x.classList.toggle('on', x === b));
  });
}

/* ---------- каркас страницы (сайдбар + шапка) ---------- */
const navFor = (role) => [
  { id: 'dashboard', label: 'Dashboard', icon: 'home' },
  { id: 'ai', label: 'AI Burmalda', icon: 'spark' },
  { id: 'transaction', label: 'Transactions', icon: 'list' },
  { id: 'wallet', label: role === 'accountant' ? 'Finance' : 'My wallet', icon: 'wallet' },
  { id: 'tasks', label: 'Tasks', icon: 'task' },
  { id: 'reports', label: 'Reports', icon: 'report' }
];

/* хуки очистки при уходе со страницы (таймеры и т.п.) */
const _leave = [];
function onLeave(fn) { _leave.push(fn); }

/* ---------- раскрытие виджета ---------- */
const NO_EXPAND = 'button:not(.arrow-btn), a, input, textarea, select, .composer, [data-noexpand]';
let _xOpen = false;

/** Открывает увеличенную версию карточки; закрывается кликом вне окна, Esc или крестиком. */
function expandCard(card, { title, html, mount }) {
  if (_xOpen) return;
  _xOpen = true;
  const r = card.getBoundingClientRect();
  const ov = document.createElement('div');
  ov.className = 'xov';
  ov.innerHTML = `<div class="xcard" role="dialog" aria-modal="true" aria-label="${esc(title)}">
    <div class="xhead"><h2>${esc(title)}</h2><button class="icon-btn xclose" aria-label="Close">${icon('close')}</button></div>
    <div class="xbody">${html}</div></div>`;
  document.body.appendChild(ov);
  document.body.classList.add('noscroll');
  const box = $('.xcard', ov);
  if (mount) mount(box);
  const f = box.getBoundingClientRect();
  const from = `translate(${r.left - f.left}px, ${r.top - f.top}px) scale(${r.width / f.width}, ${r.height / f.height})`;
  const reduce = window.matchMedia && matchMedia('(prefers-reduced-motion: reduce)').matches;
  const dur = reduce ? 0 : 340;
  card.style.visibility = 'hidden';
  box.style.transformOrigin = 'top left';
  ov.animate([{ opacity: 0 }, { opacity: 1 }], { duration: dur, fill: 'both' });
  box.animate([{ transform: from }, { transform: 'none' }], { duration: dur, easing: 'cubic-bezier(.2,.85,.2,1)', fill: 'both' });
  $('.xbody', box).animate([{ opacity: 0 }, { opacity: 0, offset: .35 }, { opacity: 1 }], { duration: dur, fill: 'both' });

  let closing = false;
  const close = () => {
    if (closing) return; closing = true;
    document.removeEventListener('keydown', onKey);
    const r2 = card.getBoundingClientRect(), f2 = box.getBoundingClientRect();
    const to = `translate(${r2.left - f2.left}px, ${r2.top - f2.top}px) scale(${r2.width / f2.width}, ${r2.height / f2.height})`;
    ov.animate([{ opacity: 1 }, { opacity: 0 }], { duration: dur, fill: 'forwards' });
    $('.xbody', box).animate([{ opacity: 1 }, { opacity: 0 }], { duration: dur * .5, fill: 'forwards' });
    const a = box.animate([{ transform: 'none' }, { transform: to }], { duration: dur, easing: 'cubic-bezier(.4,0,.2,1)', fill: 'forwards' });
    a.onfinish = () => { card.style.visibility = ''; ov.remove(); document.body.classList.remove('noscroll'); _xOpen = false; };
  };
  const onKey = (e) => { if (e.key === 'Escape') close(); };
  document.addEventListener('keydown', onKey);
  ov.addEventListener('mousedown', (e) => { if (!e.target.closest('.xcard')) close(); });
  $('.xclose', ov).onclick = close;
  box.closeExpanded = close;
}

/** Делает карточку раскрываемой. build() возвращает {title, html, mount?} и вызывается при клике. */
function makeExpandable(card, build) {
  if (!card) return;
  card.classList.add('expandable');
  card.tabIndex = 0;
  const open = () => expandCard(card, build());
  card.addEventListener('click', (e) => { if (!e.target.closest(NO_EXPAND)) open(); });
  card.addEventListener('keydown', (e) => { if ((e.key === 'Enter' || e.key === ' ') && e.target === card) { e.preventDefault(); open(); } });
}

/**
 * Строит каркас и вызывает render(viewId, container) при смене раздела.
 * role: 'employee' | 'accountant'
 */
function mountShell({ role, render }) {
  const user = API.currentUser();
  if (!user) { location.replace('login.html'); return null; }
  if (user.role !== role) { location.replace(user.role === 'accountant' ? 'accountant.html' : 'employee.html'); return null; }

  const roleLabel = role === 'accountant' ? 'Accountant' : 'Personal Account';
  document.body.innerHTML = `
  <div class="app">
    <aside class="sidebar" id="sidebar">
      <div class="logo">${LOGO}<span>Burmalda</span></div>
      <div style="position:relative">
        <button class="profile" id="profileBtn" aria-haspopup="true">
          <div class="avatar sm">${esc(initials(user.name))}</div>
          <div><div class="pname">${esc(user.name)}</div><div class="prole">${roleLabel}</div></div>
          <span class="chev">${icon('chev')}</span>
        </button>
        <div class="menu-pop" id="profileMenu">
          <button data-act="settings">Settings</button>
          <button data-act="logout">Logout</button>
        </div>
      </div>
      <div class="nav-label">Main menu</div>
      <nav class="nav" id="nav">
        ${navFor(role).map((n) => `<button data-view="${n.id}">${icon(n.icon)}<span>${n.label}</span></button>`).join('')}
      </nav>
      <div class="nav-spacer"></div>
      <div class="nav-label" style="font-size:14px">Preference</div>
      <nav class="nav">
        <button data-act="settings">${icon('gear')}<span>Settings</span></button>
        <button data-act="help">${icon('help')}<span>Help Center</span></button>
        <button data-act="logout">${icon('logout')}<span>Logout</span></button>
      </nav>
    </aside>
    <div class="content">
      <header class="topbar">
        <button class="burger" id="burger" aria-label="Menu">${icon('menu')}</button>
        <div class="top-title"><h1 id="pageTitle">Home</h1><p>“Track reports and tasks easily with AI insights and recommendations”</p></div>
        <label class="search">${icon('search')}<input id="searchInp" placeholder="Search anything" autocomplete="off"></label>
        <button class="icon-btn" data-act="settings" aria-label="Settings">${icon('gear')}</button>
        <button class="icon-btn" data-act="help" aria-label="Help">${icon('help')}</button>
        <div class="avatar" title="${esc(user.name)}">${esc(initials(user.name))}</div>
      </header>
      <main id="view"></main>
    </div>
  </div>`;

  const sidebar = $('#sidebar');
  const view = $('#view');
  const titles = { dashboard: 'Home' };
  navFor(role).forEach((n) => { if (!titles[n.id]) titles[n.id] = n.label; });

  function go(id) {
    while (_leave.length) { try { _leave.pop()(); } catch (e) {} }
    $$('#nav button').forEach((b) => b.classList.toggle('active', b.dataset.view === id));
    $('#pageTitle').textContent = titles[id];
    sidebar.classList.remove('open');
    view.innerHTML = '';
    view.dataset.page = id;
    window.scrollTo(0, 0);
    if (render(id, view) === false) {
      view.innerHTML = `<div class="placeholder-view"><div class="card"><h2>${esc(titles[id])}</h2>
        <p class="sub" style="margin-top:8px">Раздел в разработке.</p></div></div>`;
    }
    try { location.hash = id; } catch (e) {}
  }

  const actions = {
    settings: openSettings,
    help: () => openModal({ title: 'Help Center', sub: 'Нужна помощь? Напишите нам на support@burmalda.com', actions: [{ label: 'OK', ghost: true }] }),
    logout: async () => { await API.logout(); location.replace('login.html'); }
  };
  document.addEventListener('click', (e) => {
    const a = e.target.closest('[data-act]');
    if (a && actions[a.dataset.act]) { $('#profileMenu').classList.remove('open'); actions[a.dataset.act](); return; }
    const v = e.target.closest('#nav button');
    if (v) go(v.dataset.view);
    if (!e.target.closest('#profileBtn')) $('#profileMenu').classList.remove('open');
  });
  $('#profileBtn').onclick = () => $('#profileMenu').classList.toggle('open');
  $('#burger').onclick = () => sidebar.classList.toggle('open');
  $('#searchInp').addEventListener('keydown', async (e) => {
    if (e.key === 'Enter' && e.target.value.trim()) {
      await API.search(e.target.value.trim());
      toast('Поиск: «' + e.target.value.trim() + '» — подключится к бэкенду');
    }
  });

  const start = (location.hash || '#dashboard').slice(1);
  go(titles[start] ? start : 'dashboard');
  return { user, view };
}

/* ---------- AI-чат (используется в обеих ролях) ---------- */
function chatHTML({ title, role }) {
  return `
  <div class="ai-card" data-chat>
    <h2>${esc(title)}</h2>
    <div class="ai-hello" data-hello>${LOGO_MARK}
      <div class="hi">Good to see you!</div>
      <h3>How Can I Assist You With Your Projects?</h3>
      <p>Streamline workflows, get project insights, and manage tasks-all in your command center.</p>
    </div>
    <div class="chat-log" data-log></div>
    <div class="composer">
      <textarea data-input rows="1" placeholder="Ask anything…" aria-label="Ask anything"></textarea>
      <div class="tools">
        <button type="button" aria-label="Add">${icon('plus')}</button>
        <button type="button" aria-label="Voice">${icon('mic')}</button>
        <button type="button" aria-label="Image">${icon('image')}</button>
        <button type="button" class="send" data-send aria-label="Send">${icon('send')}</button>
      </div>
    </div>
  </div>`;
}

function bindChat(root, role) {
  const input = $('[data-input]', root), log = $('[data-log]', root), hello = $('[data-hello]', root);
  const add = (cls, text) => {
    const m = document.createElement('div'); m.className = 'msg ' + cls; m.textContent = text;
    log.appendChild(m); log.classList.add('has'); hello.style.display = 'none'; log.scrollTop = log.scrollHeight; return m;
  };
  async function send() {
    const q = input.value.trim(); if (!q) return;
    input.value = ''; add('me', q);
    const wait = add('ai', '…');
    try { wait.textContent = await API.askAI(q, role); } catch (e) { wait.textContent = 'Ошибка: ' + e.message; }
    log.scrollTop = log.scrollHeight;
  }
  $('[data-send]', root).onclick = send;
  input.addEventListener('keydown', (e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); } });
}

/* ---------- Графики ---------- */
function smoothPath(pts) {
  if (pts.length < 2) return '';
  let d = `M${pts[0][0]},${pts[0][1]}`;
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[i - 1] || pts[i], p1 = pts[i], p2 = pts[i + 1], p3 = pts[i + 2] || p2;
    const c1 = [p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6];
    const c2 = [p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6];
    d += ` C${c1[0].toFixed(1)},${c1[1].toFixed(1)} ${c2[0].toFixed(1)},${c2[1].toFixed(1)} ${p2[0]},${p2[1]}`;
  }
  return d;
}
function seriesPoints(values, w, h, pad = 0, max = 100) {
  return values.map((v, i) => [pad + (i * (w - pad * 2)) / (values.length - 1), +(h - (v / max) * h).toFixed(1)]);
}
function polar(cx, cy, r, deg) {
  const a = (deg * Math.PI) / 180; return [cx + r * Math.cos(a), cy + r * Math.sin(a)];
}
function arcPath(cx, cy, r, from, to) {
  const [x1, y1] = polar(cx, cy, r, from), [x2, y2] = polar(cx, cy, r, to);
  return `M${x1.toFixed(1)},${y1.toFixed(1)} A${r},${r} 0 ${to - from > 180 ? 1 : 0} 1 ${x2.toFixed(1)},${y2.toFixed(1)}`;
}
