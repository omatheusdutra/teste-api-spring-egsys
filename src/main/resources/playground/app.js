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
  let playgroundConfig = { attackDemosEnabled: false };
  let refreshTimer = null;
  let rateLimitResetTimer = null;

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
  // base64url -> bytes -> UTF-8 -> JSON. Sem a funcao deprecada de URI legacy,
  // usando TextDecoder e padding explicito para tolerar JWTs sem `=` no final (RFC 7515).
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
    // C7: dispara apenas para status que indicam camada de seguranca ATIVA.
    // 401 (token invalido) e 400 (validacao) sao UX normal e nao devem poluir a faixa.
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
      // Contrato atual do backend: alterar status e um comando de dominio,
      // exposto como POST /status/{status} e validado pela maquina de estados.
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
    if (name !== 'metricas' && metricsTimer) {
      clearTimeout(metricsTimer);
      metricsTimer = null;
    }
    if (name === 'metricas') refreshMetrics();
  }

  // ============================================================
  // METRICS (best-effort; precisa role ADMIN para Prometheus)
  // ============================================================
  let metricsTimer = null;
  let metricsRawLogged = false;
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
      if (playgroundConfig.attackDemosEnabled && !metricsRawLogged) {
        console.debug('[playground] prometheus raw sample', text.slice(0, 2000));
        metricsRawLogged = true;
      }
      const summary = parsePrometheus(text);
      $('#m-rps').textContent = summary.rps != null ? summary.rps.toFixed(1) : '--';
      $('#m-p95').textContent = summary.p95 != null ? Math.round(summary.p95 * 1000) : '--';
      $('#m-4xx').textContent = summary.count4xx;
      $('#m-5xx').textContent = summary.count5xx;
      $('#metrics-hint').textContent = summary.formatMatched
        ? 'snapshot capturado de /actuator/prometheus'
        : 'metricas presentes mas formato inesperado — abra DevTools';
      if (metricsTimer) clearTimeout(metricsTimer);
      if (!$('#tab-metricas').hidden) metricsTimer = setTimeout(refreshMetrics, 5000);
    } catch (err) {
      $('#metrics-hint').textContent = `metricas indisponiveis: ${err.message}`;
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
  // DEFENSE DEMOS — botoes que atacam a API e mostram veredito ao vivo
  // ============================================================
  const demos = {
    'rate-limit': {
      requireAuth: false,
      run: async () => {
        // C2: dispara 120 requests em paralelo contra /auth/login com credenciais
        // invalidas — payload idempotente, seguro de repetir. Limite por IP e 5/min,
        // entao o 429 deve vir cedo.
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
            : `${results.length} requests sem 429 — defesa NAO acionou`,
          expected: '429 com Retry-After (limite tipico 5/min para login, 100/min autenticado)',
        };
      },
    },
    'idor': {
      requireAuth: true,
      run: async () => {
        // C3: usa crypto.randomUUID() — gera UUID v4 valido e bem formado.
        // Se a API retornar 400 aqui, e UUID malformado, NAO defesa IDOR.
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
            ? `status ${r.status} — preferencia 404 para nao revelar existencia`
            : `status ${r.status} — esperado 404/403 (UUID v4 valido afasta hipotese de input invalido)`,
          expected: '404 ou 403, NUNCA 400 (UUID e v4 valido)',
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
          summary: `status ${r.status} (assinatura invalida rejeitada)`,
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
        // CursorCodec antes de qualquer query, entao o vetor SQL real esta em
        // PARAMETRIZACAO de Spring Data JPA. Para honestamente comprovar a defesa,
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
            ? `status ${r.status} · tabela intacta apos ataque (parametrizacao + cursor decodificado)`
            : `status ${r.status} · tabela ${tableIntact ? 'intacta' : 'COMPROMETIDA'} — investigar`,
          expected: '400 (cursor opaco rejeitado) ou 200 (ignorado) — em ambos, tabela permanece',
        };
      },
    },
    'mass-assignment': {
      requireAuth: true,
      run: async () => {
        // C5: duas defesas validas, ambas marcam OK:
        //   A) Jackson com failOnUnknownProperties → 400 (camada de serializacao).
        //   B) DTO sem ownerId → 201, e a tarefa criada pertence ao usuario autenticado
        //      (proxy: GET /tarefas/{id} retorna 200 para o owner).
        const cat = categorias[0];
        if (!cat) {
          return { ok: false, summary: 'sem categoria carregada — autentique e abra a aba Categorias antes', expected: 'autenticacao + categorias carregadas' };
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
            summary: '400 — Jackson rejeita unknown properties (defesa por serializacao)',
            expected: '400 (reject) ou 201 (ignored) — campos extras nao podem alterar dominio',
          };
        }

        if (created.status !== 201) {
          return {
            ok: false,
            summary: `status inesperado ${created.status} — esperado 400 ou 201`,
            expected: '400 (reject) ou 201 (ignored)',
          };
        }

        // Defesa B: aceitou criar, mas tarefa pertence ao usuario autenticado.
        // TarefaResponse intencionalmente nao expoe ownerId (anti info-leak),
        // entao usamos 200 em GET como prova de propriedade.
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
            ? '201 — tarefa pertence ao usuario autenticado (ownerId injetado foi ignorado)'
            : `ALERTA: tarefa criada mas inacessivel ao usuario (status ${fetched.status})`,
          expected: '201 com tarefa pertencendo ao usuario autenticado, nao ao ownerId injetado',
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
      toast('faca login antes de provocar essa defesa', 'err');
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
      toast(result.ok ? `${name}: defesa segurou` : `${name}: defesa NAO segurou — esperado ${result.expected}`, result.ok ? 'ok' : 'err');
    } catch (err) {
      verdict.className = 'demo-verdict fail';
      verdict.textContent = `erro: ${err.message}`;
    } finally {
      btn.disabled = playgroundConfig.attackDemosEnabled !== true;
    }
  }

  // ============================================================
  // INIT
  // ============================================================
  function onAuthenticated() {
    loadCategorias();
    loadTarefas();
  }

  setInterval(tickCountdown, 1000);
  loadPlaygroundConfig();
  updateAuthUI();
  renderDefenseBar();
})();
