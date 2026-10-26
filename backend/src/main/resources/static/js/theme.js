/* Тема: light / dark / system. Подключать в <head> ДО отрисовки, чтобы не было мигания. */
(function () {
  var KEY = 'burmalda_theme';
  var mq = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;

  function saved() {
    try { return localStorage.getItem(KEY) || 'system'; } catch (e) { return 'system'; }
  }
  function resolve(mode) {
    if (mode === 'system') return mq && mq.matches ? 'dark' : 'light';
    return mode;
  }
  function apply(mode) {
    document.documentElement.setAttribute('data-theme', resolve(mode));
  }

  window.Theme = {
    get: saved,
    current: function () { return resolve(saved()); },
    set: function (mode) {
      try { localStorage.setItem(KEY, mode); } catch (e) {}
      apply(mode);
      document.dispatchEvent(new CustomEvent('themechange', { detail: mode }));
    },
    toggle: function () { this.set(this.current() === 'dark' ? 'light' : 'dark'); }
  };

  if (mq && mq.addEventListener) {
    mq.addEventListener('change', function () { if (saved() === 'system') apply('system'); });
  }
  apply(saved());
})();
