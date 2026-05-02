(() => {
  'use strict';

  // ============================================================
  // STATE: tokens vivem somente em variaveis no closure desta IIFE.
  // Nao usamos localStorage nem sessionStorage — narrativa de seguranca consistente.
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
  let refreshTimer = null;

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

  function toast(msg, kind = 'ok') {
    const host = $('#toast-host');
    const t = el('div', { class: `toast ${kind}` }, [msg]);
    host.append(t);
    setTimeout(() => t.remove(), 3500);
  }

  // ============================================================
  // JWT decode (somente para exibir exp/sub/roles — quem valida e o servidor).
  // ============================================================
  function decodeJwt(token) {
    try {
      const [, payload] = token.split('.');
      const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
      return JSON.parse(decodeURIComponent(escape(json)));
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
    if (status === 429) flashDefense('rate-limit');
    else if (status === 403) flashDefense('rbac/idor');
    else if (status === 401) flashDefense('auth-rejected');
    else if (status === 415) flashDefense('content-type');
    else if (status === 413) flashDefense('payload-too-large');
    else if (status === 400) flashDefense('input-validation');
    if (response.headers.get('Retry-After')) {
      defenseStats.rateLimit.current = defenseStats.rateLimit.max;
      renderDefenseBar();
    }
  }

  function flashDefense(name) {
    defenseStats.lastTriggered = name;
    const pill = $('#pill-defense');
    pill.textContent = `ultima defesa: ${name}`;
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
      $('#pill-defense').textContent = `ultima defesa: ${defenseStats.lastTriggered}`;
    }
  }

  function formatDuration(ms) {
    if (ms <= 0) return 'expirado';
    const totalSec = Math.floor(ms / 1000);
    const m = Math.floor(totalSec / 60);
    const s = totalSec % 60;
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
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
    const fireIn = Math.max(5000, remaining - 60_000); // refresh com 60s de folga
    refreshTimer = setTimeout(autoRefresh, fireIn);
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
  async function loadCategorias() {
    if (!accessToken) return;
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
    if (tarefas.length === 0) {
      host.replaceChildren(el('div', { class: 'hint' }, ['nenhuma tarefa ainda — crie uma acima']));
      return;
    }
    host.replaceChildren(...tarefas.map(t => {
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
          el('button', { class: 'btn btn-ghost', type: 'button', onclick: () => alterarStatus(t.id, 'em-andamento') }, ['Em andamento']),
          el('button', { class: 'btn btn-ghost', type: 'button', onclick: () => alterarStatus(t.id, 'concluida') }, ['Concluir']),
          el('button', { class: 'btn btn-danger', type: 'button', onclick: () => excluirTarefa(t.id) }, ['Excluir']),
        ]),
      ]);
    }));
  }

  async function alterarStatus(id, status) {
    try {
      await api('POST', `/api/v1/tarefas/${id}/status/${status}`);
      toast(`status alterado para ${status}`, 'ok');
      loadTarefas();
    } catch (err) {
      toast(`falhou: ${err.message}`, 'err');
    }
  }

  async function excluirTarefa(id) {
    if (!confirm('Confirmar exclusao? (soft delete)')) return;
    try {
      await api('DELETE', `/api/v1/tarefas/${id}`);
      toast('tarefa excluida', 'ok');
      loadTarefas();
    } catch (err) {
      toast(`falha: ${err.message}`, 'err');
    }
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

  $('#login-form').addEventListener('submit', async ev => {
    ev.preventDefault();
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

  $('#refresh-btn').addEventListener('click', autoRefresh);
  $('#logout-btn').addEventListener('click', async () => {
    try {
      await api('POST', '/api/v1/auth/logout');
      toast('logout (jti revogado no Redis)', 'ok');
    } catch (err) { /* segue limpando local mesmo se API falhar */ }
    clearTokens();
  });

  $('#tarefa-form').addEventListener('submit', async ev => {
    ev.preventDefault();
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
    } catch (err) { toast(`criacao falhou: ${err.message}`, 'err'); }
  });

  $('#edit-form').addEventListener('submit', async ev => {
    if (ev.submitter && ev.submitter.value === 'cancel') return;
    ev.preventDefault();
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
    } catch (err) { toast(`atualizacao falhou: ${err.message}`, 'err'); }
  });

  $('#reset-btn').addEventListener('click', () => {
    clearTokens();
    tarefas = []; categorias = [];
    auditLog.length = 0;
    renderTarefas(); renderCategorias(); renderAudit();
    $('#req-method').replaceChildren(el('span', { class: 'badge badge-muted' }, ['--']), ' ', el('span', {}, ['aguardando...']));
    $('#req-headers').textContent = '--';
    $('#req-body').textContent = '--';
    $('#res-meta').textContent = '--';
    $('#res-body').textContent = '--';
    toast('estado resetado', 'ok');
  });

  // ============================================================
  // TABS
  // ============================================================
  $$('.tab').forEach(tab => {
    tab.addEventListener('click', () => activateTab(tab.dataset.tab));
  });

  function activateTab(name) {
    $$('.tab').forEach(t => t.setAttribute('aria-selected', String(t.dataset.tab === name)));
    $$('.tab-panel').forEach(p => { p.hidden = (p.id !== `tab-${name}`); });
    if (name === 'metricas') refreshMetrics();
  }

  // ============================================================
  // METRICS (best-effort; precisa role ADMIN para Prometheus)
  // ============================================================
  let metricsTimer = null;
  async function refreshMetrics() {
    if (!accessToken) {
      $('#metrics-hint').textContent = 'faca login primeiro.';
      return;
    }
    try {
      const r = await fetch('/actuator/prometheus', { headers: { Authorization: `Bearer ${accessToken}` } });
      if (r.status === 403) {
        $('#metrics-hint').textContent = 'usuario sem ROLE_ADMIN — Prometheus exige privilegio.';
        return;
      }
      if (!r.ok) {
        $('#metrics-hint').textContent = `actuator respondeu ${r.status}`;
        return;
      }
      const text = await r.text();
      const summary = parsePrometheus(text);
      $('#m-rps').textContent = summary.rps != null ? summary.rps.toFixed(1) : '--';
      $('#m-p95').textContent = summary.p95 != null ? Math.round(summary.p95 * 1000) : '--';
      $('#m-4xx').textContent = summary.count4xx;
      $('#m-5xx').textContent = summary.count5xx;
      $('#metrics-hint').textContent = 'snapshot capturado de /actuator/prometheus';
      if (metricsTimer) clearTimeout(metricsTimer);
      metricsTimer = setTimeout(refreshMetrics, 5000);
    } catch (err) {
      $('#metrics-hint').textContent = `metricas indisponiveis: ${err.message}`;
    }
  }

  function parsePrometheus(text) {
    const lines = text.split('\n');
    let totalCount = 0, count4xx = 0, count5xx = 0;
    let p95 = null;
    for (const line of lines) {
      if (line.startsWith('#')) continue;
      const match = line.match(/^http_server_requests_seconds_count\{(.+?)\} ([\d.eE+-]+)/);
      if (match) {
        const labels = match[1];
        const value = Number(match[2]);
        totalCount += value;
        const status = labels.match(/status="(\d+)"/)?.[1];
        if (status?.startsWith('4')) count4xx += value;
        else if (status?.startsWith('5')) count5xx += value;
        continue;
      }
      const p95Match = line.match(/^http_server_requests_seconds\{[^}]*quantile="0\.95"[^}]*\} ([\d.eE+-]+)/);
      if (p95Match) p95 = Math.max(p95 || 0, Number(p95Match[1]));
    }
    return { rps: totalCount > 0 ? totalCount / 60 : null, p95, count4xx, count5xx };
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
    } catch (_) { toast('clipboard indisponivel', 'err'); }
  });

  $('#repeat-req').addEventListener('click', () => {
    if (!lastRequest) return;
    const path = lastRequest.url.startsWith('/') ? lastRequest.url : new URL(lastRequest.url).pathname + new URL(lastRequest.url).search;
    api(lastRequest.method, path, lastRequest.body, { auth: !!accessToken }).catch(() => {});
  });

  // ============================================================
  // INIT
  // ============================================================
  function onAuthenticated() {
    loadCategorias();
    loadTarefas();
  }

  setInterval(tickCountdown, 1000);
  updateAuthUI();
  renderDefenseBar();
})();
