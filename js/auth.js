/* Страница входа / регистрации */
(function () {
  const home = (u) => (u.role === 'accountant' ? 'accountant.html' : 'employee.html');

  // уже вошёл — сразу на свой дашборд
  const existing = API.currentUser();
  if (existing) { location.replace(home(existing)); return; }

  $('.auth-hero .logo').innerHTML = LOGO + '<span>Burmalda</span>';

  // кнопка темы
  const themeBtn = $('#themeBtn');
  const paintTheme = () => { themeBtn.innerHTML = Theme.current() === 'dark' ? icon('sun') : icon('moon'); };
  paintTheme();
  themeBtn.onclick = () => { Theme.toggle(); paintTheme(); };

  const msg = $('#msg');
  const show = (text, type) => { msg.textContent = text; msg.className = 'auth-msg show ' + type; };
  const hide = () => { msg.className = 'auth-msg'; };

  // вкладки
  const loginForm = $('#loginForm'), regForm = $('#regForm');
  function tab(name) {
    hide();
    $$('.tabs button').forEach((b) => b.classList.toggle('on', b.dataset.tab === name));
    loginForm.hidden = name !== 'login';
    regForm.hidden = name !== 'register';
    $('#title').textContent = name === 'login' ? 'Welcome back' : 'Create account';
    $('#subtitle').textContent = name === 'login' ? 'Sign in to your account' : 'Sign up as an accountant or an employee';
  }
  $$('.tabs button').forEach((b) => b.onclick = () => tab(b.dataset.tab));

  // демо-подстановка
  $$('[data-fill]').forEach((c) => c.onclick = () => {
    $('#lEmail').value = c.dataset.fill; $('#lPass').value = 'demo1234';
  });

  // выбор роли
  let role = 'employee';
  $$('#rolePick button').forEach((b) => b.onclick = () => {
    role = b.dataset.role;
    $$('#rolePick button').forEach((x) => x.classList.toggle('on', x === b));
    $('#deptField').style.display = role === 'employee' ? '' : 'none';
  });

  const emailOk = (e) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e);

  loginForm.addEventListener('submit', async (e) => {
    e.preventDefault(); hide();
    const email = $('#lEmail').value.trim(), pass = $('#lPass').value;
    if (!emailOk(email)) return show('Enter a valid email', 'err');
    if (!pass) return show('Enter your password', 'err');
    const btn = $('#loginBtn'); btn.disabled = true; btn.textContent = 'Signing in…';
    try {
      const user = await API.login(email, pass);
      location.replace(home(user));
    } catch (err) {
      show(err.message, 'err');
      btn.disabled = false; btn.textContent = 'Sign in';
    }
  });

  regForm.addEventListener('submit', async (e) => {
    e.preventDefault(); hide();
    const data = {
      role,
      name: $('#rName').value.trim(),
      email: $('#rEmail').value.trim(),
      company: $('#rCompany').value.trim(),
      department: role === 'accountant' ? 'Finance' : $('#rDept').value,
      password: $('#rPass').value
    };
    if (data.name.length < 2) return show('Enter your name', 'err');
    if (!emailOk(data.email)) return show('Enter a valid email', 'err');
    if (!data.company) return show('Enter your company', 'err');
    if (data.password.length < 8) return show('Password must be at least 8 characters', 'err');
    if (data.password !== $('#rPass2').value) return show('Passwords do not match', 'err');

    const btn = $('#regBtn'); btn.disabled = true; btn.textContent = 'Creating…';
    try {
      await API.register(data);
      regForm.reset(); btn.disabled = false; btn.textContent = 'Create account';
      tab('login');
      $('#lEmail').value = data.email;
      show('Account created! Now sign in.', 'ok');
    } catch (err) {
      show(err.message, 'err');
      btn.disabled = false; btn.textContent = 'Create account';
    }
  });
})();
