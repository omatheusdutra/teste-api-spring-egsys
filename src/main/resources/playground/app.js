(() => {
  'use strict';

  // ============================================================
  // STATE: tokens vivem somente em variáveis no closure desta IIFE.
  // Não usamos localStorage nem sessionStorage — narrativa de segurança consistente.
  // ============================================================
  let accessToken = null;
  let refreshToken = null;
  let accessExpiresAt = 0; // epoch ms
  let refreshExpiresAt = 0;
  let currentUser = null;  // { sub, email, roles, exp }
  let categorias = [];
  let tarefas = [];
  let lastRequest = null;  // { method, url, headers, body, status, latencyMs, responseText }
  const auditLog = [];
  const defenseStats = { rateLimit: { current: 0, max: 100 }, lastTriggered: null };
  let playgroundConfig = { attackDemosEnabled: false };
  let refreshTimer = null;
  let rateLimitResetTimer = null;
  const pendingDeleteTimers = new Map();
  let taskSearch = '';
  let currentLang = (navigator.language || 'pt-BR').startsWith('pt') ? 'pt-BR' : 'en-US';
  let healthTimer = null;
  const latencyHistory = [];

  const i18n = {
    'pt-BR': {
      'lang.toggle': 'EN',
      'auth.title': 'Autenticação',
      'task.title': 'Tarefas',
      'task.hint': 'CRUD completo via JWT',
      'task.search': 'Buscar tarefa',
      'task.search.placeholder': 'título, descrição, categoria ou status',
      'task.create': 'Criar tarefa',
      'tasks.empty.title': 'Seu quadro está limpo',
      'tasks.empty.body': 'Crie a primeira tarefa para ver o fluxo completo de API, auditoria e métricas acontecendo ao vivo.',
      'tasks.empty.cta': 'Criar primeira tarefa',
      'tabs.tasks': 'Tarefas',
      'tabs.categories': 'Categorias',
      'tabs.audit': 'Auditoria',
      'tabs.metrics': 'Métricas',
      'health.pending': 'health: --',
      'health.up': 'health: UP',
      'health.warn': 'health: degradado',
      'health.down': 'health: down',
    },
    'en-US': {
      'lang.toggle': 'PT',
      'auth.title': 'Authentication',
      'task.title': 'Tasks',
      'task.hint': 'Full CRUD via JWT',
      'task.search': 'Search task',
      'task.search.placeholder': 'title, description, category or status',
      'task.create': 'Create task',
      'tasks.empty.title': 'Your board is clear',
      'tasks.empty.body': 'Create the first task to watch the API, audit trail, and metrics flow live.',
      'tasks.empty.cta': 'Create first task',
      'tabs.tasks': 'Tasks',
      'tabs.categories': 'Categories',
      'tabs.audit': 'Audit',
      'tabs.metrics': 'Metrics',
      'health.pending': 'health: --',
      'health.up': 'health: UP',
      'health.warn': 'health: degraded',
      'health.down': 'health: down',
    },
  };

  function t(key) {
    return i18n[currentLang]?.[key] || i18n['pt-BR'][key] || key;
  }

  // ============================================================
  // DOM helpers (nunca usar innerHTML para dados do servidor).
  // ============================================================
  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

  function el(tag, attrs = {}, children = []) {
    const node = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs)) {
      if (v == null) continue;
      if (k === 'class') node.className = v;
      else if (k === 'data') for (const [dk, dv] of Object.entries(v)) node.dataset[dk] = dv;
      else if (k.startsWith('on') && typeof v === 'function') node.addEventListener(k.slice(2).toLowerCase(), v);
      else node.setAttribute(k, v);
    }
    for (const c of [].concat(children)) {
      if (c == null) continue;
      node.append(c instanceof Node ? c : document.createTextNode(String(c)));
    }
    return node;
  }

  function toast(msg, kind = 'ok', opts = {}) {
    const host = $('#toast-host');
    const children = [msg];
    if (opts.actionLabel && typeof opts.onAction === 'function') {
      children.push(el('button', { class: 'toast-action', type: 'button', onclick: opts.onAction }, [opts.actionLabel]));
    }
    const t = el('div', { class: `toast ${kind}` }, children);
    host.append(t);
    setTimeout(() => t.remove(), opts.durationMs || 3500);
    return t;
  }

  async function withLoadingState(button, task) {
    if (!button) return task();
    const originalText = button.textContent;
    button.disabled = true;
    button.classList.add('btn-loading');
    button.textContent = '...';
    try {
      return await task();
    } finally {
      button.textContent = originalText;
      button.classList.remove('btn-loading');
      button.disabled = false;
    }
  }

  function confirmAction(message) {
    const dlg = $('#confirm-dialog');
    if (!dlg) return Promise.resolve(false);
    $('#confirm-message').textContent = message;
    dlg.returnValue = '';
    return new Promise(resolve => {
      const onClose = () => {
        dlg.removeEventListener('close', onClose);
        resolve(dlg.returnValue === 'ok');
      };
      dlg.addEventListener('close', onClose);
      dlg.showModal();
    });
  }

  function attackDemosUnavailableMessage() {
    return 'Demonstrações ofensivas disponíveis apenas em ambiente controlado.';
  }

  async function loadPlaygroundConfig() {
    applyPlaygroundConfig();
    try {
      const response = await fetch('/playground/config', {
        headers: { Accept: 'application/json' },
        credentials: 'omit',
      });
      if (response.ok) {
        playgroundConfig = await response.json();
      }
    } catch (_) {
      playgroundConfig = { attackDemosEnabled: false };
    }
    applyPlaygroundConfig();
  }

  function applyPlaygroundConfig() {
    const attackDemosEnabled = playgroundConfig.attackDemosEnabled === true;
    const card = $('#defense-demos-card');
    const message = $('#defense-demos-disabled');
    if (card) card.classList.toggle('demo-locked', !attackDemosEnabled);
    if (message) message.hidden = attackDemosEnabled;
    $$('.demo-btn').forEach(btn => {
      btn.disabled = !attackDemosEnabled;
      btn.setAttribute('aria-disabled', String(!attackDemosEnabled));
    });
  }

  // ============================================================
  // JWT decode (somente para exibir exp/sub/roles — quem valida e o servidor).
  // base64url -> bytes -> UTF-8 -> JSON. Sem a função deprecada de URI legacy,
  // usando TextDecoder e padding explícito para tolerar JWTs sem `=` no final (RFC 7515).
  // ============================================================
  function decodeJwt(token) {
    try {
      const [, payload] = token.split('.');
      const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
      const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
      const binary = atob(padded);
      const bytes = Uint8Array.from(binary, c => c.charCodeAt(0));
      const json = new TextDecoder('utf-8').decode(bytes);
      return JSON.parse(json);
    } catch (_) { return null; }
  }

  function maskToken(token) {
    if (!token) return '';
    return token.length > 16 ? `${token.slice(0, 12)}...${token.slice(-4)}` : token;
  }

  // ============================================================
  // HTTP layer: registra cada chamada na auditoria, atualiza painel
  // de request/response, dispara faixa de defesas em 4xx/5xx.
  // ============================================================
  async function api(method, path, body, opts = {}) {
    const url = path.startsWith('http') ? path : path;
    const headers = { 'Accept': 'application/json' };
    if (body) headers['Content-Type'] = 'application/json';
    if (opts.auth !== false && accessToken) headers['Authorization'] = `Bearer ${accessToken}`;

    const started = performance.now();
    let response, text = '', status = 0;
    try {
      response = await fetch(url, {
        method,
        headers,
        body: body ? JSON.stringify(body) : undefined,
        credentials: 'omit',
      });
      status = response.status;
      text = await response.text();
    } catch (err) {
      const latency = Math.round(performance.now() - started);
      recordRequest({ method, url, headers, body, status: 0, latencyMs: latency, responseText: String(err) });
      throw err;
    }
    const latency = Math.round(performance.now() - started);
    recordRequest({ method, url, headers, body, status, latencyMs: latency, responseText: text });
    detectDefenseTrigger(response, status);

    let parsed = null;
    if (text && response.headers.get('content-type')?.includes('json')) {
      try { parsed = JSON.parse(text); } catch (_) { parsed = null; }
    }
    if (status >= 400) {
      const detail = parsed?.detail || parsed?.title || text || `HTTP ${status}`;
      const error = new Error(detail);
      error.status = status;
      error.body = parsed;
      throw error;
    }
    return parsed;
  }

  function recordRequest(rec) {
    lastRequest = rec;
    renderReqResPanels(rec);
    pushAudit(rec);
  }

  function pushAudit(rec) {
    auditLog.unshift({ ...rec, ts: new Date() });
    if (auditLog.length > 80) auditLog.length = 80;
    renderAudit();
  }

  function renderReqResPanels(rec) {
    const methodHost = $('#req-method');
    methodHost.replaceChildren(
      el('span', { class: `badge badge-${rec.method.toLowerCase()}` }, [rec.method]),
      ' ',
      el('span', { id: 'req-url' }, [rec.url]),
    );
    const headersDisplay = { ...rec.headers };
    if (headersDisplay.Authorization) {
      headersDisplay.Authorization = `Bearer ${maskToken(headersDisplay.Authorization.replace('Bearer ', ''))}`;
    }
    $('#req-headers').textContent = JSON.stringify(headersDisplay, null, 2);
    $('#req-body').textContent = rec.body ? JSON.stringify(rec.body, null, 2) : '(sem body)';

    const statusKind = rec.status === 0 ? 'err' : rec.status >= 500 ? 'err' : rec.status >= 400 ? 'warn' : 'ok';
    const meta = $('#res-meta');
    meta.replaceChildren(
      el('span', { class: `badge badge-${statusKind}` }, [String(rec.status || 'ERR')]),
      ' ',
      el('span', {}, [`${rec.latencyMs} ms`]),
    );
    let pretty = rec.responseText;
    try { pretty = JSON.stringify(JSON.parse(rec.responseText), null, 2); } catch (_) {}
    $('#res-body').textContent = pretty || '(sem corpo)';

    $('#copy-curl').disabled = false;
    $('#repeat-req').disabled = false;
  }

  function renderAudit() {
    const host = $('#audit-list');
    host.replaceChildren(...auditLog.map(rec => {
      const kind = rec.status === 0 ? 'err' : rec.status >= 500 ? 'err' : rec.status >= 400 ? 'warn' : 'ok';
      return el('div', { class: `audit-row ${kind}` }, [
        el('span', { class: `badge badge-${rec.method.toLowerCase()}` }, [rec.method]),
        el('span', {}, [rec.url]),
        el('span', {}, [String(rec.status)]),
        el('span', {}, [`${rec.latencyMs} ms`]),
      ]);
    }));
  }

  function detectDefenseTrigger(response, status) {
    // C7: dispara apenas para status que indicam camada de segurança ATIVA.
    // 401 (token inválido) e 400 (validação) são UX normal e não devem poluir a faixa.
    if (status === 429) flashDefense('rate-limit');
    else if (status === 403) flashDefense('rbac/idor');
    else if (status === 413) flashDefense('payload-too-large');
    else if (status === 415) flashDefense('content-type');

    // C6: ao receber Retry-After, marca a pill em max e agenda decremento real.
    const retryAfter = response.headers.get('Retry-After');
    if (retryAfter) {
      defenseStats.rateLimit.current = defenseStats.rateLimit.max;
      renderDefenseBar();
      const seconds = Math.max(1, Number(retryAfter) || 60);
      if (rateLimitResetTimer) clearTimeout(rateLimitResetTimer);
      rateLimitResetTimer = setTimeout(() => {
        defenseStats.rateLimit.current = 0;
        rateLimitResetTimer = null;
        renderDefenseBar();
      }, seconds * 1000);
    }
  }

  function flashDefense(name) {
    defenseStats.lastTriggered = name;
    const pill = $('#pill-defense');
    pill.textContent = `última defesa: ${name}`;
    pill.classList.remove('flash');
    void pill.offsetWidth; // reflow
    pill.classList.add('flash');
    renderDefenseBar();
  }

  function renderDefenseBar() {
    const rl = defenseStats.rateLimit;
    $('#pill-rate').textContent = `rate limit: ${rl.current}/${rl.max} req/min`;
    if (accessExpiresAt) {
      const remaining = Math.max(0, accessExpiresAt - Date.now());
      $('#pill-jwt').textContent = `jwt: ${formatDuration(remaining)}`;
    } else {
      $('#pill-jwt').textContent = 'jwt: --';
    }
    if (defenseStats.lastTriggered) {
      $('#pill-defense').textContent = `última defesa: ${defenseStats.lastTriggered}`;
    }
  }

  function formatDuration(ms) {
    if (ms <= 0) return 'expirado';
    const totalSec = Math.floor(ms / 1000);
    const m = Math.floor(totalSec / 60);
    const s = totalSec % 60;
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }

  function setText(selector, key) {
    const node = $(selector);
    if (node) node.textContent = t(key);
  }

  function applyI18n() {
    document.documentElement.lang = currentLang;
    $('#lang-toggle').textContent = t('lang.toggle');
    setText('.auth-card h2', 'auth.title');
    setText('#tab-btn-tarefas', 'tabs.tasks');
    setText('#tab-btn-categorias', 'tabs.categories');
    setText('#tab-btn-auditoria', 'tabs.audit');
    setText('#tab-btn-metricas', 'tabs.metrics');
    setText('#tab-tarefas h2', 'task.title');
    setText('#tab-tarefas .card-header .hint', 'task.hint');
    const searchLabel = $('.search-label');
    if (searchLabel?.firstChild) searchLabel.firstChild.textContent = t('task.search') + ' ';
    $('#task-search').placeholder = t('task.search.placeholder');
    $('#tarefa-form button[type=submit]').textContent = t('task.create');
    renderTarefas();
  }

  function toggleLanguage() {
    currentLang = currentLang === 'pt-BR' ? 'en-US' : 'pt-BR';
    applyI18n();
  }

  // ============================================================
  // AUTH UI
  // ============================================================
  function setTokens(resp) {
    accessToken = resp.accessToken;
    refreshToken = resp.refreshToken;
    accessExpiresAt = new Date(resp.accessExpiresAt).getTime();
    refreshExpiresAt = new Date(resp.refreshExpiresAt).getTime();
    currentUser = decodeJwt(accessToken);
    updateAuthUI();
    scheduleAutoRefresh();
    refreshHealth();
    onAuthenticated();
  }

  function clearTokens() {
    accessToken = refreshToken = null;
    accessExpiresAt = refreshExpiresAt = 0;
    currentUser = null;
    if (refreshTimer) { clearTimeout(refreshTimer); refreshTimer = null; }
    updateAuthUI();
  }

  function updateAuthUI() {
    const badge = $('#auth-badge');
    badge.classList.remove('badge-muted', 'badge-ok', 'badge-warn');
    if (!accessToken) {
      badge.classList.add('badge-muted');
      badge.textContent = 'sem token';
      $('#refresh-btn').disabled = true;
      $('#logout-btn').disabled = true;
      $('#countdown').textContent = 'access expira em --:--';
      return;
    }
    badge.classList.add('badge-ok');
    const sub = currentUser?.sub || '?';
    badge.textContent = `autenticado · ${sub.slice(0, 8)}`;
    $('#refresh-btn').disabled = false;
    $('#logout-btn').disabled = false;
    tickCountdown();
  }

  function tickCountdown() {
    if (!accessToken) return;
    const remaining = accessExpiresAt - Date.now();
    $('#countdown').textContent = `access expira em ${formatDuration(remaining)}`;
    renderDefenseBar();
  }

  function scheduleAutoRefresh() {
    if (refreshTimer) clearTimeout(refreshTimer);
    const remaining = accessExpiresAt - Date.now();
    if (remaining <= 60_000) {
      autoRefresh();
      return;
    }
    refreshTimer = setTimeout(autoRefresh, remaining - 60_000);
  }

  async function autoRefresh() {
    if (!refreshToken) return;
    try {
      const resp = await api('POST', '/api/v1/auth/refresh', { refreshToken }, { auth: false });
      setTokens(resp);
      toast('access token rotacionado', 'ok');
    } catch (err) {
      toast(`refresh falhou: ${err.message}`, 'err');
      clearTokens();
    }
  }

  // ============================================================
  // CRUD: Categorias e Tarefas
  // ============================================================
  function renderSkeletonRows(host, count = 3) {
    host.replaceChildren(...Array.from({ length: count }, () => el('div', { class: 'skeleton-row', 'aria-hidden': 'true' })));
  }

  async function loadCategorias() {
    if (!accessToken) return;
    renderSkeletonRows($('#categoria-list'));
    try {
      categorias = await api('GET', '/api/v1/categorias');
      populateCategoriaSelects();
      renderCategorias();
    } catch (err) {
      toast(`falha ao listar categorias: ${err.message}`, 'err');
    }
  }

  function populateCategoriaSelects() {
    const formSel = $('#tarefa-form select[name=categoriaId]');
    const editSel = $('#edit-form select[name=categoriaId]');
    [formSel, editSel].forEach(sel => {
      sel.replaceChildren(el('option', { value: '' }, ['--']));
      for (const c of categorias) sel.append(el('option', { value: c.id }, [c.descricao]));
    });
  }

  function renderCategorias() {
    const host = $('#categoria-list');
    host.replaceChildren(...categorias.map(c =>
      el('div', { class: 'task-row' }, [
        el('div', {}, [
          el('div', { class: 'task-title' }, [c.descricao]),
          el('div', { class: 'task-meta' }, [`id: ${c.id}`]),
        ]),
      ])
    ));
  }

  async function loadTarefas() {
    if (!accessToken) return;
    renderSkeletonRows($('#tarefa-list'));
    try {
      const page = await api('GET', '/api/v1/tarefas?limit=100');
      tarefas = page.items || [];
      renderTarefas();
    } catch (err) {
      toast(`falha ao listar tarefas: ${err.message}`, 'err');
    }
  }

  function renderTarefas() {
    const host = $('#tarefa-list');
    const query = taskSearch.trim().toLowerCase();
    const visibleTarefas = tarefas
      .filter(t => !pendingDeleteTimers.has(t.id))
      .filter(t => !query || [
        t.titulo,
        t.descricao || '',
        t.categoria?.descricao || '',
        t.status || '',
      ].some(value => value.toLowerCase().includes(query)));
    if (visibleTarefas.length === 0) {
      host.replaceChildren(renderEmptyTasksState());
      return;
    }
    host.replaceChildren(...visibleTarefas.map(t => {
      const statusKind = t.status === 'CONCLUIDA' ? 'ok' : t.status === 'CANCELADA' ? 'err' : 'warn';
      return el('div', { class: 'task-row' }, [
        el('div', {}, [
          el('div', { class: 'task-title' }, [t.titulo]),
          el('div', { class: 'task-meta' }, [
            el('span', { class: `badge badge-${statusKind}` }, [t.status]),
            ' · ',
            new Date(t.dataHora).toLocaleString('pt-BR'),
            ' · ',
            t.categoria.descricao,
          ]),
          t.descricao ? el('div', { class: 'task-desc' }, [t.descricao]) : null,
        ]),
        el('div', { class: 'task-actions' }, [
          el('button', { class: 'btn btn-ghost', type: 'button', onclick: () => openEdit(t) }, ['Editar']),
          el('button', { class: 'btn btn-ghost', type: 'button', onclick: ev => withLoadingState(ev.currentTarget, () => alterarStatus(t.id, 'em-andamento')) }, ['Em andamento']),
          el('button', { class: 'btn btn-ghost', type: 'button', onclick: ev => withLoadingState(ev.currentTarget, () => alterarStatus(t.id, 'concluida')) }, ['Concluir']),
          el('button', { class: 'btn btn-danger', type: 'button', onclick: ev => withLoadingState(ev.currentTarget, () => excluirTarefa(t.id)) }, ['Excluir']),
        ]),
      ]);
    }));
  }

  function renderEmptyTasksState() {
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('viewBox', '0 0 120 90');
    svg.setAttribute('aria-hidden', 'true');
    [
      ['rect', { x: '22', y: '24', width: '76', height: '46', rx: '10', fill: 'none', stroke: 'currentColor', 'stroke-width': '4', opacity: '0.45' }],
      ['path', { d: 'M36 42h30M36 54h18', fill: 'none', stroke: 'currentColor', 'stroke-width': '4', 'stroke-linecap': 'round', opacity: '0.7' }],
      ['path', { d: 'M72 52l8 8 16-20', fill: 'none', stroke: '#10E098', 'stroke-width': '5', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }],
      ['circle', { cx: '27', cy: '22', r: '4', fill: 'currentColor', opacity: '0.35' }],
      ['circle', { cx: '95', cy: '72', r: '3', fill: 'currentColor', opacity: '0.35' }],
    ].forEach(([tag, attrs]) => {
      const node = document.createElementNS('http://www.w3.org/2000/svg', tag);
      for (const [name, value] of Object.entries(attrs)) node.setAttribute(name, value);
      svg.append(node);
    });
    return el('div', { class: 'empty-state' }, [
      svg,
      el('strong', {}, [t('tasks.empty.title')]),
      el('p', {}, [t('tasks.empty.body')]),
      el('button', {
        class: 'btn btn-primary',
        type: 'button',
        onclick: () => $('#tarefa-form input[name=titulo]')?.focus(),
      }, [t('tasks.empty.cta')]),
    ]);
  }

  async function alterarStatus(id, status) {
    try {
      // Contrato atual do backend: alterar status é um comando de domínio,
      // exposto como POST /status/{status} e validado pela máquina de estados.
      await api('POST', `/api/v1/tarefas/${id}/status/${status}`);
      toast(`status alterado para ${status}`, 'ok');
      loadTarefas();
    } catch (err) {
      toast(`falhou: ${err.message}`, 'err');
    }
  }

  async function excluirTarefa(id) {
    if (pendingDeleteTimers.has(id)) return;
    const timer = setTimeout(async () => {
      pendingDeleteTimers.delete(id);
      try {
        await api('DELETE', `/api/v1/tarefas/${id}`);
        toast('tarefa excluida com soft delete', 'ok');
        loadTarefas();
      } catch (err) {
        toast(`falha: ${err.message}`, 'err');
        renderTarefas();
      }
    }, 8000);
    pendingDeleteTimers.set(id, timer);
    renderTarefas();
    toast('exclusão agendada por 8s', 'ok', {
      durationMs: 8000,
      actionLabel: 'Desfazer',
      onAction: () => {
        const pending = pendingDeleteTimers.get(id);
        if (pending) clearTimeout(pending);
        pendingDeleteTimers.delete(id);
        renderTarefas();
        toast('exclusão cancelada', 'ok');
      },
    });
  }

  function openEdit(t) {
    const dlg = $('#edit-dialog');
    const form = $('#edit-form');
    form.elements.id.value = t.id;
    form.elements.titulo.value = t.titulo;
    form.elements.descricao.value = t.descricao || '';
    form.elements.categoriaId.value = t.categoria.id;
    form.elements.dataHora.value = toLocalDateTime(t.dataHora);
    dlg.showModal();
  }

  function toLocalDateTime(iso) {
    const d = new Date(iso);
    const pad = n => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  function fromLocalDateTime(s) {
    return new Date(s).toISOString();
  }

  // ============================================================
  // FORM HANDLERS
  // ============================================================
  $('#register-form').addEventListener('submit', async ev => {
    ev.preventDefault();
    await withLoadingState(ev.submitter, async () => {
      const form = ev.target;
      try {
        const resp = await api('POST', '/api/v1/auth/register', {
          email: form.elements.email.value,
          password: form.elements.password.value,
        }, { auth: false });
        setTokens(resp);
        toast('registrado e autenticado', 'ok');
        form.reset();
      } catch (err) { toast(`registro falhou: ${err.message}`, 'err'); }
    });
  });

  $('#login-form').addEventListener('submit', async ev => {
    ev.preventDefault();
    await withLoadingState(ev.submitter, async () => {
      const form = ev.target;
      try {
        const resp = await api('POST', '/api/v1/auth/login', {
          email: form.elements.email.value,
          password: form.elements.password.value,
        }, { auth: false });
        setTokens(resp);
        toast('login ok', 'ok');
        form.reset();
      } catch (err) { toast(`login falhou: ${err.message}`, 'err'); }
    });
  });

  $('#refresh-btn').addEventListener('click', ev => withLoadingState(ev.currentTarget, autoRefresh));
  $('#logout-btn').addEventListener('click', ev => withLoadingState(ev.currentTarget, async () => {
    if (!(await confirmAction('Revogar o JWT atual no Redis e limpar a sessão local?'))) return;
    try {
      await api('POST', '/api/v1/auth/logout');
      toast('logout (jti revogado no Redis)', 'ok');
    } catch (err) { /* segue limpando local mesmo se API falhar */ }
    clearTokens();
  }));

  $('#tarefa-form').addEventListener('submit', async ev => {
    ev.preventDefault();
    await withLoadingState(ev.submitter, async () => {
      const form = ev.target;
      const body = {
        titulo: form.elements.titulo.value.trim(),
        descricao: form.elements.descricao.value.trim() || null,
        categoriaId: form.elements.categoriaId.value,
        dataHora: fromLocalDateTime(form.elements.dataHora.value),
      };
      try {
        await api('POST', '/api/v1/tarefas', body);
        toast('tarefa criada', 'ok');
        form.reset();
        loadTarefas();
      } catch (err) { toast(`criação falhou: ${err.message}`, 'err'); }
    });
  });

  $('#edit-form').addEventListener('submit', async ev => {
    if (ev.submitter && ev.submitter.value === 'cancel') return;
    ev.preventDefault();
    await withLoadingState(ev.submitter, async () => {
      const form = ev.target;
      const id = form.elements.id.value;
      const body = {
        titulo: form.elements.titulo.value.trim(),
        descricao: form.elements.descricao.value.trim() || null,
        categoriaId: form.elements.categoriaId.value,
        dataHora: fromLocalDateTime(form.elements.dataHora.value),
      };
      try {
        await api('PUT', `/api/v1/tarefas/${id}`, body);
        toast('tarefa atualizada', 'ok');
        $('#edit-dialog').close();
        loadTarefas();
      } catch (err) { toast(`atualização falhou: ${err.message}`, 'err'); }
    });
  });

  $('#task-search').addEventListener('input', ev => {
    taskSearch = ev.target.value;
    renderTarefas();
  });

  $('#lang-toggle').addEventListener('click', toggleLanguage);

  $('#reset-btn').addEventListener('click', ev => withLoadingState(ev.currentTarget, async () => {
    if (!(await confirmAction('Limpar tokens em memória, listas e auditoria da sessão?'))) return;
    clearTokens();
    tarefas = []; categorias = [];
    pendingDeleteTimers.forEach(timer => clearTimeout(timer));
    pendingDeleteTimers.clear();
    auditLog.length = 0;
    renderTarefas(); renderCategorias(); renderAudit();
    $('#req-method').replaceChildren(el('span', { class: 'badge badge-muted' }, ['--']), ' ', el('span', {}, ['aguardando...']));
    $('#req-headers').textContent = '--';
    $('#req-body').textContent = '--';
    $('#res-meta').textContent = '--';
    $('#res-body').textContent = '--';
    toast('estado resetado', 'ok');
  }));

  // ============================================================
  // TABS
  // ============================================================
  $$('.tab').forEach(tab => {
    tab.addEventListener('click', () => activateTab(tab.dataset.tab));
    tab.addEventListener('keydown', handleTabKeydown);
  });

  function activateTab(name, focus = false) {
    $$('.tab').forEach(t => {
      const active = t.dataset.tab === name;
      t.setAttribute('aria-selected', String(active));
      t.tabIndex = active ? 0 : -1;
      if (active && focus) t.focus();
    });
    $$('.tab-panel').forEach(p => { p.hidden = (p.id !== `tab-${name}`); });
    if (name !== 'metricas' && metricsTimer) {
      clearTimeout(metricsTimer);
      metricsTimer = null;
    }
    if (name === 'metricas') refreshMetrics();
  }

  function handleTabKeydown(ev) {
    const tabs = $$('.tab');
    const idx = tabs.indexOf(ev.currentTarget);
    let next = null;
    if (ev.key === 'ArrowRight') next = tabs[(idx + 1) % tabs.length];
    else if (ev.key === 'ArrowLeft') next = tabs[(idx - 1 + tabs.length) % tabs.length];
    else if (ev.key === 'Home') next = tabs[0];
    else if (ev.key === 'End') next = tabs[tabs.length - 1];
    if (!next) return;
    ev.preventDefault();
    activateTab(next.dataset.tab, true);
  }

  // ============================================================
  // METRICS (best-effort; precisa role ADMIN para Prometheus)
  // ============================================================
  let metricsTimer = null;
  let metricsRawLogged = false;
  async function refreshMetrics() {
    if (!accessToken) {
      $('#metrics-hint').textContent = 'faça login primeiro.';
      return;
    }
    try {
      const r = await fetch('/actuator/prometheus', { headers: { Authorization: `Bearer ${accessToken}` } });
      if (r.status === 403) {
        $('#metrics-hint').textContent = 'usuário sem ROLE_ADMIN — Prometheus exige privilégio.';
        return;
      }
      if (!r.ok) {
        $('#metrics-hint').textContent = `actuator respondeu ${r.status}`;
        return;
      }
      const text = await r.text();
      if (playgroundConfig.attackDemosEnabled && !metricsRawLogged) {
        console.debug('[playground] prometheus raw sample', text.slice(0, 2000));
        metricsRawLogged = true;
      }
      const summary = parsePrometheus(text);
      $('#m-rps').textContent = summary.rps != null ? summary.rps.toFixed(1) : '--';
      const p95Ms = summary.p95 != null ? Math.round(summary.p95 * 1000) : null;
      $('#m-p95').textContent = p95Ms != null ? p95Ms : '--';
      $('#m-4xx').textContent = summary.count4xx;
      $('#m-5xx').textContent = summary.count5xx;
      if (p95Ms != null) pushLatencySample(p95Ms);
      $('#metrics-hint').textContent = summary.formatMatched
        ? 'snapshot capturado de /actuator/prometheus'
        : 'métricas presentes mas formato inesperado — abra DevTools';
      if (metricsTimer) clearTimeout(metricsTimer);
      if (!$('#tab-metricas').hidden) metricsTimer = setTimeout(refreshMetrics, 5000);
    } catch (err) {
      $('#metrics-hint').textContent = `métricas indisponíveis: ${err.message}`;
    }
  }

  function parsePrometheus(text) {
    const lines = text.split('\n');
    let totalCount = 0, count4xx = 0, count5xx = 0;
    let p95 = null;
    let formatMatched = false;
    for (const line of lines) {
      if (line.startsWith('#')) continue;
      const match = line.match(/^http_server_requests_seconds_(?:count|total)\{(.+?)\} ([\d.eE+-]+)/);
      if (match) {
        formatMatched = true;
        const labels = match[1];
        const value = Number(match[2]);
        totalCount += value;
        const status = labels.match(/status="(\d+)"/)?.[1];
        if (status?.startsWith('4')) count4xx += value;
        else if (status?.startsWith('5')) count5xx += value;
        continue;
      }
      const p95Match = line.match(/^http_server_requests_seconds\{[^}]*quantile="0\.95"[^}]*\} ([\d.eE+-]+)/);
      if (p95Match) {
        formatMatched = true;
        p95 = Math.max(p95 || 0, Number(p95Match[1]));
      }
    }
    return { rps: totalCount > 0 ? totalCount / 60 : null, p95, count4xx, count5xx, formatMatched };
  }

  function pushLatencySample(valueMs) {
    latencyHistory.push(valueMs);
    if (latencyHistory.length > 30) latencyHistory.shift();
    renderLatencySparkline();
  }

  function renderLatencySparkline() {
    const svg = $('#latency-sparkline');
    if (!svg) return;
    svg.replaceChildren();
    if (latencyHistory.length < 2) return;
    const width = 240;
    const height = 48;
    const max = Math.max(100, ...latencyHistory);
    const points = latencyHistory.map((v, i) => {
      const x = (i / (latencyHistory.length - 1)) * (width - 12) + 6;
      const y = height - 6 - ((v / max) * (height - 12));
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    }).join(' ');
    const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path.setAttribute('d', `M ${points.replaceAll(' ', ' L ')}`);
    svg.classList.toggle('latency-hot', latencyHistory.at(-1) > 500);
    svg.classList.toggle('latency-warn', latencyHistory.at(-1) >= 100 && latencyHistory.at(-1) <= 500);
    svg.append(path);
  }

  // ============================================================
  // CURL + REPEAT
  // ============================================================
  $('#copy-curl').addEventListener('click', async () => {
    if (!lastRequest) return;
    const lines = [`curl -X ${lastRequest.method} '${lastRequest.url}'`];
    for (const [k, v] of Object.entries(lastRequest.headers || {})) {
      const val = k === 'Authorization' ? `Bearer ${maskToken(v.replace('Bearer ', ''))}` : v;
      lines.push(`  -H '${k}: ${val}'`);
    }
    if (lastRequest.body) lines.push(`  -d '${JSON.stringify(lastRequest.body)}'`);
    const curl = lines.join(' \\\n');
    try {
      await navigator.clipboard.writeText(curl);
      toast('curl copiado (token mascarado)', 'ok');
    } catch (_) { toast('clipboard indisponível', 'err'); }
  });

  $('#repeat-req').addEventListener('click', () => {
    if (!lastRequest) return;
    const path = lastRequest.url.startsWith('/') ? lastRequest.url : new URL(lastRequest.url).pathname + new URL(lastRequest.url).search;
    api(lastRequest.method, path, lastRequest.body, { auth: !!accessToken }).catch(() => {});
  });

  // ============================================================
  // DEFENSE DEMOS — botões que atacam a API e mostram veredito ao vivo
  // ============================================================
  const demos = {
    'rate-limit': {
      requireAuth: false,
      run: async () => {
        // C2: dispara 120 requests em paralelo contra /auth/login com credenciais
        // inválidas — payload idempotente, seguro de repetir. Limite por IP e 5/min,
        // então o 429 deve vir cedo.
        const target = '/api/v1/auth/login';
        const promises = Array.from({ length: 120 }, () =>
          fetch(target, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email: 'naoexiste@example.com', password: 'senha-invalida-12!' }),
            credentials: 'omit',
          }).then(r => r.status).catch(() => 0)
        );
        const results = await Promise.all(promises);
        const got429 = results.includes(429);
        const firstBlocked = results.findIndex(s => s === 429);
        return {
          ok: got429,
          summary: got429
            ? `${results.length} requests · primeiro 429 em #${firstBlocked + 1} · status vistos: ${[...new Set(results)].sort().join(', ')}`
            : `${results.length} requests sem 429 — defesa NÃO acionou`,
          expected: '429 com Retry-After (limite típico 5/min para login, 100/min autenticado)',
        };
      },
    },
    'idor': {
      requireAuth: true,
      run: async () => {
        // C3: usa crypto.randomUUID() — gera UUID v4 válido e bem formado.
        // Se a API retornar 400 aqui, é UUID malformado, NÃO defesa IDOR.
        const randomUuid = crypto.randomUUID();
        const url = `/api/v1/tarefas/${randomUuid}`;
        const r = await fetch(url, {
          headers: { Authorization: `Bearer ${accessToken}`, Accept: 'application/json' },
        });
        recordRequest({
          method: 'GET', url,
          headers: { Authorization: `Bearer ${accessToken}` }, body: null,
          status: r.status, latencyMs: 0, responseText: await r.text(),
        });
        const ok = r.status === 404 || r.status === 403;
        return {
          ok,
          summary: ok
            ? `status ${r.status} — preferência 404 para não revelar existência`
            : `status ${r.status} — esperado 404/403 (UUID v4 válido afasta hipótese de input inválido)`,
          expected: '404 ou 403, NUNCA 400 (UUID é v4 válido)',
        };
      },
    },
    'tampered-jwt': {
      requireAuth: true,
      run: async () => {
        const tampered = accessToken.slice(0, -4) + 'AAAA';
        const r = await fetch('/api/v1/tarefas', {
          headers: { Authorization: `Bearer ${tampered}`, Accept: 'application/json' },
        });
        recordRequest({
          method: 'GET', url: '/api/v1/tarefas', headers: { Authorization: `Bearer ${maskToken(tampered)}` },
          body: null, status: r.status, latencyMs: 0, responseText: await r.text(),
        });
        return {
          ok: r.status === 401,
          summary: `status ${r.status} (assinatura inválida rejeitada)`,
          expected: '401',
        };
      },
    },
    'alg-none': {
      requireAuth: false,
      run: async () => {
        const header = btoa(JSON.stringify({ alg: 'none', typ: 'JWT' })).replace(/=+$/, '');
        const payload = btoa(JSON.stringify({
          sub: '00000000-0000-0000-0000-000000000000', iss: 'egsys-tasks-api',
          aud: 'egsys-tasks-clients', exp: Math.floor(Date.now() / 1000) + 3600,
          roles: ['ROLE_ADMIN'],
        })).replace(/=+$/, '');
        const forged = `${header}.${payload}.`;
        const r = await fetch('/api/v1/tarefas', {
          headers: { Authorization: `Bearer ${forged}`, Accept: 'application/json' },
        });
        recordRequest({
          method: 'GET', url: '/api/v1/tarefas', headers: { Authorization: `Bearer ${maskToken(forged)}` },
          body: null, status: r.status, latencyMs: 0, responseText: await r.text(),
        });
        return {
          ok: r.status === 401,
          summary: `status ${r.status} (alg=none rejeitado antes da assinatura)`,
          expected: '401',
        };
      },
    },
    'sqli': {
      requireAuth: true,
      run: async () => {
        // C4: cursor injection. O endpoint /api/v1/tarefas decodifica `cursor` via
        // CursorCodec antes de qualquer query, então o vetor SQL real está em
        // PARAMETRIZAÇÃO de Spring Data JPA. Para honestamente comprovar a defesa,
        // (1) snapshot, (2) ataque, (3) verifica que a tabela continua respondendo
        // e ainda lista tarefas — DROP TABLE teria derrubado tudo.
        const headers = { Authorization: `Bearer ${accessToken}`, Accept: 'application/json' };

        const pre = await fetch('/api/v1/tarefas?limit=1', { headers });
        if (!pre.ok) {
          return { ok: false, summary: `pre-check falhou (${pre.status}) — abortando`, expected: 'API responsiva antes do ataque' };
        }

        const payload = encodeURIComponent("'; DROP TABLE tarefas; --");
        const url = `/api/v1/tarefas?cursor=${payload}&limit=10`;
        const r = await fetch(url, { headers });
        recordRequest({
          method: 'GET', url, headers: { Authorization: `Bearer ${accessToken}` },
          body: null, status: r.status, latencyMs: 0, responseText: await r.text(),
        });

        const post = await fetch('/api/v1/tarefas?limit=1', { headers });
        const tableIntact = post.ok;

        const cursorHandledSafely = r.status === 400 || r.ok;
        const ok = tableIntact && cursorHandledSafely;
        return {
          ok,
          summary: ok
            ? `status ${r.status} · tabela intacta após ataque (parametrização + cursor decodificado)`
            : `status ${r.status} · tabela ${tableIntact ? 'intacta' : 'COMPROMETIDA'} — investigar`,
          expected: '400 (cursor opaco rejeitado) ou 200 (ignorado) — em ambos, tabela permanece',
        };
      },
    },
    'mass-assignment': {
      requireAuth: true,
      run: async () => {
        // C5: duas defesas válidas, ambas marcam OK:
        //   A) Jackson com failOnUnknownProperties → 400 (camada de serialização).
        //   B) DTO sem ownerId → 201, e a tarefa criada pertence ao usuário autenticado
        //      (proxy: GET /tarefas/{id} retorna 200 para o owner).
        const cat = categorias[0];
        if (!cat) {
          return { ok: false, summary: 'sem categoria carregada — autentique e abra a aba Categorias antes', expected: 'autenticação + categorias carregadas' };
        }

        const injectedOwner = '00000000-0000-0000-0000-000000000000';
        const body = {
          titulo: 'tentativa de mass assignment',
          descricao: 'payload tenta injetar ownerId e role',
          categoriaId: cat.id,
          dataHora: new Date(Date.now() + 86400000).toISOString(),
          ownerId: injectedOwner,
          role: 'ADMIN',
        };
        const created = await fetch('/api/v1/tarefas', {
          method: 'POST',
          headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json', Accept: 'application/json' },
          body: JSON.stringify(body),
        });
        const createdText = await created.text();
        recordRequest({
          method: 'POST', url: '/api/v1/tarefas',
          headers: { Authorization: `Bearer ${accessToken}` }, body,
          status: created.status, latencyMs: 0, responseText: createdText,
        });

        // Defesa A: Jackson rejeita os campos extras (failOnUnknownProperties)
        if (created.status === 400) {
          return {
            ok: true,
            summary: '400 — Jackson rejeita unknown properties (defesa por serialização)',
            expected: '400 (reject) ou 201 (ignored) — campos extras não podem alterar domínio',
          };
        }

        if (created.status !== 201) {
          return {
            ok: false,
            summary: `status inesperado ${created.status} — esperado 400 ou 201`,
            expected: '400 (reject) ou 201 (ignored)',
          };
        }

        // Defesa B: aceitou criar, mas tarefa pertence ao usuário autenticado.
        // TarefaResponse intencionalmente não expõe ownerId (anti info-leak),
        // então usamos 200 em GET como prova de propriedade.
        let persisted = null;
        try { persisted = JSON.parse(createdText); } catch (_) {}
        if (!persisted?.id) {
          return { ok: false, summary: '201 mas sem id na resposta — investigar', expected: 'id na resposta' };
        }
        const fetched = await fetch(`/api/v1/tarefas/${persisted.id}`, {
          headers: { Authorization: `Bearer ${accessToken}`, Accept: 'application/json' },
        });
        const ok = fetched.ok;
        return {
          ok,
          summary: ok
            ? '201 — tarefa pertence ao usuário autenticado (ownerId injetado foi ignorado)'
            : `ALERTA: tarefa criada mas inacessível ao usuário (status ${fetched.status})`,
          expected: '201 com tarefa pertencendo ao usuário autenticado, não ao ownerId injetado',
        };
      },
    },
  };

  $$('.demo-btn').forEach(btn => {
    btn.addEventListener('click', () => runDemo(btn.dataset.demo, btn));
  });

  async function runDemo(name, btn) {
    if (playgroundConfig.attackDemosEnabled !== true) {
      toast(attackDemosUnavailableMessage(), 'err');
      applyPlaygroundConfig();
      return;
    }
    const demo = demos[name];
    if (!demo) return;
    if (demo.requireAuth && !accessToken) {
      toast('faça login antes de provocar essa defesa', 'err');
      return;
    }
    const verdict = $(`[data-verdict="${name}"]`);
    verdict.className = 'demo-verdict running';
    verdict.textContent = 'atacando...';
    btn.disabled = true;
    try {
      const result = await demo.run();
      verdict.className = `demo-verdict ${result.ok ? 'ok' : 'fail'}`;
      verdict.textContent = result.ok ? `OK · ${result.summary}` : `FALHOU · ${result.summary}`;
      toast(result.ok ? `${name}: defesa segurou` : `${name}: defesa NÃO segurou — esperado ${result.expected}`, result.ok ? 'ok' : 'err');
    } catch (err) {
      verdict.className = 'demo-verdict fail';
      verdict.textContent = `erro: ${err.message}`;
    } finally {
      btn.disabled = playgroundConfig.attackDemosEnabled !== true;
    }
  }

  // ============================================================
  // HEALTH + KEYBOARD SHORTCUTS
  // ============================================================
  async function refreshHealth() {
    const pill = $('#health-pill');
    try {
      const headers = { Accept: 'application/json' };
      if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
      const response = await fetch('/actuator/health', { headers, credentials: 'omit' });
      const text = await response.text();
      let body = {};
      try { body = JSON.parse(text); } catch (_) {}
      pill.title = text || `HTTP ${response.status}`;
      pill.classList.remove('health-up', 'health-warn', 'health-down');
      if (response.ok && body.status === 'UP') {
        pill.classList.add('health-up');
        pill.textContent = t('health.up');
      } else if (response.ok) {
        pill.classList.add('health-warn');
        pill.textContent = t('health.warn');
      } else {
        pill.classList.add('health-down');
        pill.textContent = t('health.down');
      }
    } catch (err) {
      pill.classList.remove('health-up', 'health-warn');
      pill.classList.add('health-down');
      pill.textContent = t('health.down');
      pill.title = String(err);
    } finally {
      if (healthTimer) clearTimeout(healthTimer);
      healthTimer = setTimeout(refreshHealth, 30_000);
    }
  }

  function isTypingTarget(target) {
    return ['INPUT', 'TEXTAREA', 'SELECT'].includes(target?.tagName) || target?.isContentEditable;
  }

  let pendingGoto = null;
  function handleGlobalShortcuts(ev) {
    if (ev.key === 'Escape') {
      document.querySelector('dialog[open]')?.close('cancel');
      pendingGoto = null;
      return;
    }
    if (isTypingTarget(ev.target)) return;
    if (ev.key === '?') {
      ev.preventDefault();
      $('#shortcuts-dialog').showModal();
      return;
    }
    if (ev.key === 'c') {
      ev.preventDefault();
      $('#tarefa-form input[name=titulo]')?.focus();
      return;
    }
    if (ev.key === '/') {
      ev.preventDefault();
      $('#task-search')?.focus();
      return;
    }
    if (pendingGoto === 'g') {
      const target = { t: 'tarefas', c: 'categorias', a: 'auditoria', m: 'metricas' }[ev.key];
      pendingGoto = null;
      if (target) {
        ev.preventDefault();
        activateTab(target, true);
      }
      return;
    }
    if (ev.key === 'g') {
      pendingGoto = 'g';
      setTimeout(() => { pendingGoto = null; }, 900);
    }
  }

  // ============================================================
  // INIT
  // ============================================================
  function onAuthenticated() {
    loadCategorias();
    loadTarefas();
  }

  document.addEventListener('keydown', handleGlobalShortcuts);
  applyI18n();
  setInterval(tickCountdown, 1000);
  loadPlaygroundConfig();
  refreshHealth();
  updateAuthUI();
  renderDefenseBar();
})();
