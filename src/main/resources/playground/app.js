(() => {
  'use strict';

  // Tokens ficam apenas em memória nesta sessão.
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
  let playgroundConfig = { attackDemosEnabled: false, metricsEnabled: false };
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
      'health.up': 'health: online',
      'health.warn': 'health: degradado',
      'health.protected': 'health: protegido',
      'health.down': 'health: down',
      'theater.close': 'FECHAR · ESC',
      'theater.attacker': 'attacker · pov',
      'theater.defense': 'defense grid · target pov',
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
      'health.up': 'health: online',
      'health.warn': 'health: degraded',
      'health.protected': 'health: protected',
      'health.down': 'health: down',
      'theater.close': 'CLOSE · ESC',
      'theater.attacker': 'attacker · pov',
      'theater.defense': 'defense grid · target pov',
    },
  };

  function t(key) {
    return i18n[currentLang]?.[key] || i18n['pt-BR'][key] || key;
  }

  // DOM seguro para dados vindos da API.
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
    return 'Demonstrações ofensivas ficam disponíveis apenas em ambiente dev/sandbox.';
  }

  function statusLabel(status) {
    const normalized = String(status || '').toUpperCase().replace(/-/g, '_');
    const labels = {
      PENDENTE: 'PENDENTE',
      EM_ANDAMENTO: 'EM ANDAMENTO',
      CONCLUIDA: 'CONCLUIDA',
      CANCELADA: 'CANCELADA',
    };
    return labels[normalized] || normalized.replace(/_/g, ' ');
  }

  function statusText(status) {
    return statusLabel(status).toLowerCase();
  }

  function isAuthError(err) {
    const status = Number(err?.status || err?.statusCode || 0);
    return status === 401 || status === 403;
  }

  function showAccessDenied(reason, meta = {}) {
    const dlg = $('#access-denied-dialog');
    if (!dlg) return;
    $('#access-denied-reason').textContent = reason || 'Credenciais inválidas. Verifique email e senha e tente novamente.';
    const metaHost = $('.access-denied-meta', dlg);
    const defaults = {
      status: meta.status || '401',
      attempt: meta.attempt || new Date().toLocaleTimeString('pt-BR'),
      origin: meta.origin || 'auth/login',
    };
    metaHost.replaceChildren(...Object.entries(defaults).map(([key, value]) =>
      el('span', {}, [`${key}: ${value}`])
    ));
    if (typeof dlg.showModal === 'function' && !dlg.open) dlg.showModal();
    clearTimeout(showAccessDenied.closeTimer);
    showAccessDenied.closeTimer = setTimeout(() => {
      if (dlg.open) dlg.close('timeout');
    }, 6000);
  }

  function wireAccessDeniedDialog() {
    const dlg = $('#access-denied-dialog');
    if (!dlg) return;
    $('.access-denied-close', dlg)?.addEventListener('click', () => dlg.close('close'));
    dlg.addEventListener('click', ev => {
      if (ev.target === dlg) dlg.close('backdrop');
    });
    dlg.addEventListener('close', () => clearTimeout(showAccessDenied.closeTimer));
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
      playgroundConfig = { attackDemosEnabled: false, metricsEnabled: false };
    }
    applyPlaygroundConfig();
  }

  function applyPlaygroundConfig() {
    const attackDemosEnabled = playgroundConfig.attackDemosEnabled === true;
    const card = $('#defense-demos-card');
    if (card) {
      card.hidden = !attackDemosEnabled;
      card.classList.toggle('demo-locked', !attackDemosEnabled);
    }
    $$('.demo-btn').forEach(btn => {
      btn.disabled = !attackDemosEnabled;
      btn.setAttribute('aria-disabled', String(!attackDemosEnabled));
    });
    renderMetricsProtectedState();
  }

  // Decode local apenas para exibição; a validação real acontece no backend.
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

  // Camada HTTP do playground.
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
    else if (status === 413) flashDefense('payload-too-large');
    else if (status === 415) flashDefense('content-type');

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

  // Visualização das demos defensivas.
  const theaterScripts = {
    'rate-limit': {
      title: 'flood attack · /auth/login',
      layers: ['rate-limiter', 'auth-throttle', 'audit-logger'],
      primary: 'rate-limiter',
      recon: [
        { type: 'info', text: '> target: POST /api/v1/auth/login' },
        { type: 'info', text: '> known limit: login throttle by IP/user' },
        { type: 'warn', text: '> firing 120 parallel requests with invalid credentials' },
      ],
      payloadHint: 'POST /auth/login × 120  (Promise.all)',
      expectedDefense: 'Bucket4j rate limiter on Spring filter chain',
      verdictOk: { kind: 'blocked', text: 'REQUEST BLOCKED', sub: '429 returned · attacker throttled · audit trail preserved' },
      verdictFail: { kind: 'breach', text: 'BREACH SIMULATED', sub: 'no 429 received — rate limit may not be active' },
    },
    idor: {
      title: 'idor probe · cross-tenant access',
      layers: ['jwt-verifier', 'rbac-ownership', 'response-shaper', 'audit-logger'],
      primary: 'rbac-ownership',
      recon: [
        { type: 'info', text: '> generating random uuid with crypto.randomUUID()' },
        { type: 'info', text: '> target: GET /api/v1/tarefas/{victim-id}' },
        { type: 'warn', text: '> caller has token, but no ownership over target resource' },
      ],
      payloadHint: 'GET /api/v1/tarefas/{random-uuid}',
      expectedDefense: 'repository and use case scope access by authenticated owner',
      verdictOk: { kind: 'blocked', text: 'REQUEST BLOCKED', sub: '404/403 — resource invisible to non-owner' },
      verdictFail: { kind: 'breach', text: 'BREACH SIMULATED', sub: 'unexpected status — investigate ownership checks' },
    },
    'tampered-jwt': {
      title: 'forgery attempt · signature flip',
      layers: ['jwt-alg-filter', 'jwt-signature-verifier', 'audit-logger'],
      primary: 'jwt-signature-verifier',
      recon: [
        { type: 'info', text: '> capturing legitimate token from current memory session' },
        { type: 'warn', text: '> tampering last 4 chars of signature' },
        { type: 'info', text: '> target: GET /api/v1/tarefas with mutated token' },
      ],
      payloadHint: 'Authorization: Bearer {tampered-token}',
      expectedDefense: 'RS256 signature verification rejects modified JWT',
      verdictOk: { kind: 'blocked', text: 'REQUEST BLOCKED', sub: '401 — signature mismatch detected' },
      verdictFail: { kind: 'breach', text: 'BREACH SIMULATED', sub: 'tampered token accepted — inspect JWT verification' },
    },
    'alg-none': {
      title: 'alg confusion · none algorithm',
      layers: ['jwt-alg-filter', 'jwt-signature-verifier', 'audit-logger'],
      primary: 'jwt-alg-filter',
      recon: [
        { type: 'info', text: '> forging header: { "alg": "none", "typ": "JWT" }' },
        { type: 'info', text: '> claims: roles=[ROLE_ADMIN], iss=egsys-tasks-api' },
        { type: 'warn', text: '> signature: empty (attempting alg=none bypass)' },
      ],
      payloadHint: 'Authorization: Bearer {forged-jwt-no-signature}',
      expectedDefense: 'accepted algorithms are allowlisted to RS256 only',
      verdictOk: { kind: 'blocked', text: 'REQUEST BLOCKED', sub: 'alg=none rejected before trust boundary' },
      verdictFail: { kind: 'breach', text: 'CRITICAL · BREACH SIMULATED', sub: 'alg=none accepted — inspect JJWT configuration' },
    },
    sqli: {
      title: 'sql injection · cursor payload',
      layers: ['input-validator', 'jpa-parameterizer', 'audit-logger'],
      primary: 'input-validator',
      recon: [
        { type: 'info', text: "> payload: '; DROP TABLE tarefas; --" },
        { type: 'info', text: '> target: GET /api/v1/tarefas?cursor={payload}' },
        { type: 'warn', text: '> verifying table integrity after attack request' },
      ],
      payloadHint: "GET /api/v1/tarefas?cursor='; DROP TABLE tarefas; --",
      expectedDefense: 'cursor decoder rejects opaque payload before persistence access',
      verdictOk: { kind: 'blocked', text: 'REQUEST BLOCKED', sub: 'payload rejected or neutralized · table still intact' },
      verdictFail: { kind: 'breach', text: 'CRITICAL · BREACH SIMULATED', sub: 'unexpected behavior — verify query path' },
    },
    'mass-assignment': {
      title: 'privilege escalation · field injection',
      layers: ['dto-strict-mode', 'ownership-resolver', 'audit-logger'],
      primary: 'dto-strict-mode',
      recon: [
        { type: 'info', text: '> body includes ownerId=00000000... and role=ADMIN' },
        { type: 'info', text: '> target: POST /api/v1/tarefas' },
        { type: 'warn', text: '> server must reject or re-derive ownership from JWT' },
      ],
      payloadHint: 'POST /api/v1/tarefas { titulo, ownerId: <foreign>, role: ADMIN }',
      expectedDefense: 'DTO strict mode rejects unknown properties before domain mutation',
      verdictOk: { kind: 'blocked', text: 'REQUEST BLOCKED', sub: 'injected fields rejected or ignored by server-side ownership' },
      verdictFail: { kind: 'breach', text: 'CRITICAL · BREACH SIMULATED', sub: 'injected ownership may have affected persistence' },
    },
  };

  const layerLabels = {
    'rate-limiter': 'Rate Limiter (Bucket4j)',
    'auth-throttle': 'Auth Throttle (per-IP)',
    'jwt-alg-filter': 'JWT Algorithm Filter (RS256 only)',
    'jwt-signature-verifier': 'JWT Signature Verifier',
    'jwt-verifier': 'JWT Validator (iss/aud/exp)',
    'rbac-ownership': 'RBAC · Ownership Check',
    'response-shaper': 'Response Shaper (404 over leak)',
    'input-validator': 'Input Validator',
    'jpa-parameterizer': 'JPA Parameter Binding',
    'dto-strict-mode': 'DTO Strict Mode (failOnUnknown)',
    'ownership-resolver': 'Ownership Resolver (from JWT)',
    'audit-logger': 'Audit Logger',
  };

  let theaterReturnFocus = null;

  async function openTheater(demoName, runResult, attackResult, triggerButton) {
    const script = theaterScripts[demoName];
    const dlg = $('#intrusion-theater');
    if (!script || !dlg) return;

    theaterReturnFocus = triggerButton || null;
    const logHost = $('#theater-attacker-log');
    const layerHost = $('#theater-defense-list');
    const verdictHost = $('#theater-verdict');
    const detailHost = $('#theater-defense-detail');
    logHost.replaceChildren();
    layerHost.replaceChildren();
    verdictHost.classList.remove('visible', 'blocked', 'warning', 'breach');
    verdictHost.replaceChildren();
    detailHost.classList.remove('visible');
    detailHost.textContent = '';
    $('#attacker-title').textContent = t('theater.attacker');
    $('#defense-title').textContent = t('theater.defense');
    $('#theater-close').textContent = t('theater.close');

    for (const layerKey of script.layers) {
      layerHost.append(el('li', { class: 'idle', data: { layer: layerKey } }, [
        el('span', { class: 'layer-status' }, []),
        el('span', { class: 'layer-name' }, [layerLabels[layerKey] || layerKey]),
        el('span', { class: 'layer-latency' }, ['--']),
      ]));
    }

    if (dlg.open) dlg.close();
    dlg.showModal();
    $('#theater-close').focus();

    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const baseDelay = reduced ? 0 : 60;

    await typeLine(logHost, '> establishing controlled channel to localhost:8080...', 'log-info', baseDelay);
    await typeLine(logHost, '> target acquired · sandbox=true · evidence=audit panel', 'log-info', baseDelay);
    await typeLine(logHost, `> ${script.title}`, 'log-warn', baseDelay);
    await wait(reduced ? 0 : 180);

    for (const line of script.recon) {
      await typeLine(logHost, line.text, `log-${line.type}`, baseDelay);
    }

    await wait(reduced ? 0 : 180);
    await typeLine(logHost, '> firing payload', 'log-warn', baseDelay);
    await typeLine(logHost, `  ${script.payloadHint}`, 'log-payload', baseDelay);

    for (const layerKey of script.layers) {
      const li = layerHost.querySelector(`[data-layer="${layerKey}"]`);
      li.classList.remove('idle');
      li.classList.add('checking');
      await wait(reduced ? 0 : 180);
      li.classList.remove('checking');
      li.classList.add('active');
      li.querySelector('.layer-latency').textContent =
        layerKey === script.primary ? `${attackResult.latencyMs || 0}ms` : 'pass';
      if (layerKey === script.primary) {
        detailHost.classList.add('visible');
        detailHost.textContent = `→ ${script.expectedDefense}`;
      }
    }

    await typeLine(logHost, '◀ response', 'log-response', baseDelay);
    await typeLine(logHost, `  status: ${attackResult.status || 0}`, responseLogKind(attackResult.status), baseDelay);
    await typeLine(logHost, `  latency: ${attackResult.latencyMs || 0}ms`, 'log-response', baseDelay);

    await wait(reduced ? 0 : 260);
    const verdict = runResult.ok ? script.verdictOk : script.verdictFail;
    verdictHost.classList.add('visible', verdict.kind);
    verdictHost.append(
      document.createTextNode(verdict.text),
      el('span', { class: 'theater-verdict-sub' }, [verdict.sub]),
    );
  }

  function responseLogKind(status) {
    if (status >= 500) return 'log-err';
    if (status >= 400) return 'log-response';
    return 'log-warn';
  }

  async function typeLine(host, text, kind, baseDelay) {
    const line = el('span', { class: `log-line ${kind}` }, []);
    host.append(line);
    if (baseDelay === 0) {
      line.textContent = text;
      host.scrollTop = host.scrollHeight;
      return;
    }
    line.classList.add('typing-cursor');
    for (let i = 0; i < text.length; i++) {
      line.textContent = text.slice(0, i + 1);
      host.scrollTop = host.scrollHeight;
      if (i % 3 === 0) await wait(baseDelay / 8);
    }
    line.classList.remove('typing-cursor');
    await wait(baseDelay);
  }

  function wait(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  (function wireTheaterDialog() {
    const dlg = $('#intrusion-theater');
    const closeBtn = $('#theater-close');
    if (!dlg || !closeBtn) return;
    closeBtn.addEventListener('click', () => dlg.close('user'));
    dlg.addEventListener('click', ev => {
      if (ev.target === dlg) dlg.close('backdrop');
    });
    dlg.addEventListener('close', () => {
      const target = theaterReturnFocus;
      theaterReturnFocus = null;
      if (target && typeof target.focus === 'function') {
        target.focus();
      }
    });
  })();

  // Auth UI
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

  // Categorias e tarefas
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
    host.replaceChildren(...categorias.map(c => {
      const copy = el('button', { class: 'cat-copy-btn', type: 'button' }, ['Copiar id']);
      copy.addEventListener('click', async () => {
        try {
          await navigator.clipboard.writeText(c.id);
          copy.textContent = 'Copiado ✓';
          copy.classList.add('copied');
          setTimeout(() => {
            copy.textContent = 'Copiar id';
            copy.classList.remove('copied');
          }, 1400);
        } catch (_) {
          toast('falha ao copiar', 'err');
        }
      });
      return el('div', { class: 'task-row' }, [
        el('div', { class: 'cat-icon', 'aria-hidden': 'true' }, [(c.descricao || '?').trim().charAt(0).toUpperCase() || '?']),
        el('div', { class: 'cat-info' }, [
          el('div', { class: 'task-title' }, [c.descricao]),
          el('div', { class: 'task-meta', title: c.id }, [`id: ${c.id}`]),
        ]),
        copy,
      ]);
    }));
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
        statusLabel(t.status),
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
            el('span', { class: `badge badge-${statusKind}` }, [statusLabel(t.status)]),
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
      // Endpoint mantido como comando de domínio para preservar a máquina de estados.
      await api('POST', `/api/v1/tarefas/${id}/status/${status}`);
      toast(`status alterado para ${statusText(status)}`, 'ok');
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

  // Form handlers
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
        toast('cadastro realizado · você está autenticado', 'ok');
        form.reset();
      } catch (err) {
        if (isAuthError(err)) {
          showAccessDenied('Não foi possível registrar com essas credenciais.', {
            status: err.status || '401',
            origin: 'POST /api/v1/auth/register',
          });
        } else {
          toast(`registro falhou: ${err.message}`, 'err');
        }
      }
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
        toast('login efetuado com sucesso', 'ok');
        form.reset();
      } catch (err) {
        if (isAuthError(err)) {
          showAccessDenied('Email ou senha incorretos. Verifique as credenciais e tente novamente.', {
            status: err.status || '401',
            origin: 'POST /api/v1/auth/login',
          });
        } else {
          toast(`login falhou: ${err.message}`, 'err');
        }
      }
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

  function resetRequestResponsePanels() {
    lastRequest = null;
    $('#req-method').replaceChildren(el('span', { class: 'badge badge-muted' }, ['--']), ' ', el('span', { id: 'req-url' }, ['aguardando...']));
    $('#req-headers').textContent = '--';
    $('#req-body').textContent = '--';
    $('#res-meta').textContent = '--';
    $('#res-body').textContent = '--';
    $('#copy-curl').disabled = true;
    $('#repeat-req').disabled = true;
  }

  function resetMetricsPanel() {
    if (metricsTimer) {
      clearTimeout(metricsTimer);
      metricsTimer = null;
    }
    latencyHistory.length = 0;
    $('#m-rps').textContent = '--';
    $('#m-p95').textContent = '--';
    $('#m-4xx').textContent = '--';
    $('#m-5xx').textContent = '--';
    $('#metrics-hint').textContent = 'faça login com um usuário que tenha role ADMIN para ver dados.';
    $('#latency-sparkline').replaceChildren();
    $('#latency-sparkline').classList.remove('latency-hot', 'latency-warn');
  }

  function resetDemoVerdicts() {
    $$('.demo-verdict').forEach(verdict => {
      verdict.className = 'demo-verdict';
      verdict.textContent = 'aguardando';
    });
    $$('.demo-btn').forEach(btn => {
      btn.disabled = playgroundConfig.attackDemosEnabled !== true;
    });
  }

  function resetForms() {
    ['#register-form', '#login-form', '#tarefa-form', '#edit-form'].forEach(selector => {
      const form = $(selector);
      if (form) form.reset();
    });
    taskSearch = '';
    $('#task-search').value = '';
    populateCategoriaSelects();
    document.querySelector('dialog[open]')?.close('reset');
  }

  function resetLocalSession() {
    clearTokens();
    tarefas = []; categorias = [];
    pendingDeleteTimers.forEach(timer => clearTimeout(timer));
    pendingDeleteTimers.clear();
    if (rateLimitResetTimer) {
      clearTimeout(rateLimitResetTimer);
      rateLimitResetTimer = null;
    }
    defenseStats.rateLimit.current = 0;
    defenseStats.lastTriggered = null;
    auditLog.length = 0;
    resetForms();
    renderTarefas();
    renderCategorias();
    renderAudit();
    resetRequestResponsePanels();
    resetMetricsPanel();
    resetDemoVerdicts();
    renderDefenseBar();
    refreshHealth();
  }

  $('#reset-btn').addEventListener('click', ev => withLoadingState(ev.currentTarget, async () => {
    if (!(await confirmAction('Limpar tokens em memória, campos, listas e auditoria desta sessão?'))) return;
    resetLocalSession();
    toast('sessão local resetada', 'ok');
  }));

  // Tabs
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

  // Métricas
  let metricsTimer = null;
  let metricsRawLogged = false;

  function renderMetricsProtectedState() {
    if (playgroundConfig.metricsEnabled === true) return;
    if (metricsTimer) {
      clearTimeout(metricsTimer);
      metricsTimer = null;
    }
    $('#m-rps').textContent = '--';
    $('#m-p95').textContent = '--';
    $('#m-4xx').textContent = '--';
    $('#m-5xx').textContent = '--';
    renderLatencySparkline();
    $('#metrics-hint').textContent = 'Métricas Prometheus protegidas em produção.';
  }

  async function refreshMetrics() {
    if (playgroundConfig.metricsEnabled !== true) {
      renderMetricsProtectedState();
      return;
    }
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
        ? 'telemetria operacional validada via Prometheus protegido'
        : 'métricas presentes mas formato inesperado — abra DevTools';
      if (metricsTimer) clearTimeout(metricsTimer);
      if (playgroundConfig.metricsEnabled === true && !$('#tab-metricas').hidden) {
        metricsTimer = setTimeout(refreshMetrics, 5000);
      }
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

  // Curl e repeat
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

  // Demos defensivas
  const demos = {
    'rate-limit': {
      requireAuth: false,
      run: async reportTelemetry => {
        const target = '/api/v1/auth/login';
        const started = performance.now();
        const promises = Array.from({ length: 120 }, () =>
          fetch(target, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email: 'naoexiste@example.com', password: 'senha-invalida-12!' }),
            credentials: 'omit',
          }).then(r => r.status).catch(() => 0)
        );
        const results = await Promise.all(promises);
        const latencyMs = Math.round(performance.now() - started);
        const got429 = results.includes(429);
        const firstBlocked = results.findIndex(s => s === 429);
        reportTelemetry({ status: got429 ? 429 : results.at(-1) || 0, latencyMs });
        return {
          ok: got429,
          summary: got429
            ? `${results.length} requests · primeiro 429 em #${firstBlocked + 1} · status vistos: ${[...new Set(results)].sort().join(', ')}`
            : `${results.length} requests sem 429 — defesa NÃO acionou`,
          expected: '429 com Retry-After (limite típico 5/min para login, 100/min autenticado)',
        };
      },
    },
    idor: {
      requireAuth: true,
      run: async reportTelemetry => {
        const randomUuid = crypto.randomUUID();
        const url = `/api/v1/tarefas/${randomUuid}`;
        const started = performance.now();
        const r = await fetch(url, {
          headers: { Authorization: `Bearer ${accessToken}`, Accept: 'application/json' },
        });
        const latencyMs = Math.round(performance.now() - started);
        const responseText = await r.text();
        reportTelemetry({ status: r.status, latencyMs });
        recordRequest({
          method: 'GET', url,
          headers: { Authorization: `Bearer ${accessToken}` }, body: null,
          status: r.status, latencyMs, responseText,
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
      run: async reportTelemetry => {
        const tampered = accessToken.slice(0, -4) + 'AAAA';
        const started = performance.now();
        const r = await fetch('/api/v1/tarefas', {
          headers: { Authorization: `Bearer ${tampered}`, Accept: 'application/json' },
        });
        const latencyMs = Math.round(performance.now() - started);
        const responseText = await r.text();
        reportTelemetry({ status: r.status, latencyMs });
        recordRequest({
          method: 'GET', url: '/api/v1/tarefas', headers: { Authorization: `Bearer ${maskToken(tampered)}` },
          body: null, status: r.status, latencyMs, responseText,
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
      run: async reportTelemetry => {
        const header = btoa(JSON.stringify({ alg: 'none', typ: 'JWT' })).replace(/=+$/, '');
        const payload = btoa(JSON.stringify({
          sub: '00000000-0000-0000-0000-000000000000', iss: 'egsys-tasks-api',
          aud: 'egsys-tasks-clients', exp: Math.floor(Date.now() / 1000) + 3600,
          roles: ['ROLE_ADMIN'],
        })).replace(/=+$/, '');
        const forged = `${header}.${payload}.`;
        const started = performance.now();
        const r = await fetch('/api/v1/tarefas', {
          headers: { Authorization: `Bearer ${forged}`, Accept: 'application/json' },
        });
        const latencyMs = Math.round(performance.now() - started);
        const responseText = await r.text();
        reportTelemetry({ status: r.status, latencyMs });
        recordRequest({
          method: 'GET', url: '/api/v1/tarefas', headers: { Authorization: `Bearer ${maskToken(forged)}` },
          body: null, status: r.status, latencyMs, responseText,
        });
        return {
          ok: r.status === 401,
          summary: `status ${r.status} (alg=none rejeitado antes da assinatura)`,
          expected: '401',
        };
      },
    },
    sqli: {
      requireAuth: true,
      run: async reportTelemetry => {
        const headers = { Authorization: `Bearer ${accessToken}`, Accept: 'application/json' };
        const pre = await fetch('/api/v1/tarefas?limit=1', { headers });
        if (!pre.ok) {
          reportTelemetry({ status: pre.status, latencyMs: 0 });
          return { ok: false, summary: `pre-check falhou (${pre.status}) — abortando`, expected: 'API responsiva antes do ataque' };
        }

        const payload = encodeURIComponent("'; DROP TABLE tarefas; --");
        const url = `/api/v1/tarefas?cursor=${payload}&limit=10`;
        const started = performance.now();
        const r = await fetch(url, { headers });
        const latencyMs = Math.round(performance.now() - started);
        const responseText = await r.text();
        reportTelemetry({ status: r.status, latencyMs });
        recordRequest({
          method: 'GET', url, headers: { Authorization: `Bearer ${accessToken}` },
          body: null, status: r.status, latencyMs, responseText,
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
      run: async reportTelemetry => {
        const cat = categorias[0];
        if (!cat) {
          reportTelemetry({ status: 0, latencyMs: 0 });
          return { ok: false, summary: 'sem categoria carregada — autentique e abra a aba Categorias antes', expected: 'autenticação + categorias carregadas' };
        }

        const body = {
          titulo: 'tentativa de mass assignment',
          descricao: 'payload tenta injetar ownerId e role',
          categoriaId: cat.id,
          dataHora: new Date(Date.now() + 86400000).toISOString(),
          ownerId: '00000000-0000-0000-0000-000000000000',
          role: 'ADMIN',
        };
        const started = performance.now();
        const created = await fetch('/api/v1/tarefas', {
          method: 'POST',
          headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json', Accept: 'application/json' },
          body: JSON.stringify(body),
        });
        const latencyMs = Math.round(performance.now() - started);
        const createdText = await created.text();
        reportTelemetry({ status: created.status, latencyMs });
        recordRequest({
          method: 'POST', url: '/api/v1/tarefas',
          headers: { Authorization: `Bearer ${accessToken}` }, body,
          status: created.status, latencyMs, responseText: createdText,
        });

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
    const started = performance.now();
    let attackResult = { status: 0, latencyMs: 0 };
    try {
      const result = await demo.run(meta => { attackResult = { ...attackResult, ...meta }; });
      if (!attackResult.latencyMs) attackResult.latencyMs = Math.round(performance.now() - started);
      await openTheater(name, result, attackResult, btn);
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

  // Health e atalhos
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
      } else if (response.status === 401 || response.status === 403) {
        pill.classList.add('health-warn');
        pill.textContent = t('health.protected');
      } else if (response.ok) {
        pill.classList.add('health-warn');
        pill.textContent = t('health.warn');
      } else {
        pill.classList.add('health-warn');
        pill.textContent = t('health.warn');
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

  // Init
  function onAuthenticated() {
    loadCategorias();
    loadTarefas();
  }

  document.addEventListener('keydown', handleGlobalShortcuts);
  wireAccessDeniedDialog();
  Object.entries({ 'm-rps': '⚡', 'm-p95': '⏱', 'm-4xx': '⚠', 'm-5xx': '🛑' }).forEach(([valueId, icon]) => {
    const card = $(`#${valueId}`)?.closest('.metric');
    if (card) card.dataset.icon = icon;
  });
  applyI18n();
  setInterval(tickCountdown, 1000);
  loadPlaygroundConfig();
  refreshHealth();
  updateAuthUI();
  renderDefenseBar();
})();
