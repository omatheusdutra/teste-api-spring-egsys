/* ============== PARTICLE NETWORK ============== */
(function() {
  const canvas = document.getElementById('particles');
  const ctx = canvas.getContext('2d');
  let w, h, particles = [];
  const mouse = { x: -1000, y: -1000 };

  function resize() {
    w = canvas.width = window.innerWidth * window.devicePixelRatio;
    h = canvas.height = window.innerHeight * window.devicePixelRatio;
    canvas.style.width = window.innerWidth + 'px';
    canvas.style.height = window.innerHeight + 'px';
    ctx.scale(window.devicePixelRatio, window.devicePixelRatio);
  }

  function init() {
    resize();
    const count = Math.min(80, Math.floor((window.innerWidth * window.innerHeight) / 18000));
    particles = [];
    for (let i = 0; i < count; i++) {
      particles.push({
        x: Math.random() * window.innerWidth,
        y: Math.random() * window.innerHeight,
        vx: (Math.random() - 0.5) * 0.3,
        vy: (Math.random() - 0.5) * 0.3,
        r: Math.random() * 1.5 + 0.5
      });
    }
  }

  function draw() {
    ctx.clearRect(0, 0, w, h);
    const W = window.innerWidth, H = window.innerHeight;

    for (let p of particles) {
      p.x += p.vx; p.y += p.vy;
      if (p.x < 0 || p.x > W) p.vx *= -1;
      if (p.y < 0 || p.y > H) p.vy *= -1;
      ctx.beginPath();
      ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
      ctx.fillStyle = 'rgba(255, 255, 255, 0.5)';
      ctx.fill();
    }

    for (let i = 0; i < particles.length; i++) {
      for (let j = i + 1; j < particles.length; j++) {
        const dx = particles[i].x - particles[j].x;
        const dy = particles[i].y - particles[j].y;
        const dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 130) {
          ctx.beginPath();
          ctx.moveTo(particles[i].x, particles[i].y);
          ctx.lineTo(particles[j].x, particles[j].y);
          ctx.strokeStyle = 'rgba(255, 255, 255, ' + (0.15 * (1 - dist / 130)) + ')';
          ctx.lineWidth = 0.5;
          ctx.stroke();
        }
      }
      const mdx = particles[i].x - mouse.x;
      const mdy = particles[i].y - mouse.y;
      const mdist = Math.sqrt(mdx * mdx + mdy * mdy);
      if (mdist < 180) {
        ctx.beginPath();
        ctx.moveTo(particles[i].x, particles[i].y);
        ctx.lineTo(mouse.x, mouse.y);
        ctx.strokeStyle = 'rgba(255, 107, 44, ' + (0.4 * (1 - mdist / 180)) + ')';
        ctx.lineWidth = 0.8;
        ctx.stroke();
      }
    }

    requestAnimationFrame(draw);
  }

  window.addEventListener('resize', () => { ctx.setTransform(1,0,0,1,0,0); init(); });
  window.addEventListener('mousemove', e => { mouse.x = e.clientX; mouse.y = e.clientY; });
  window.addEventListener('mouseleave', () => { mouse.x = -1000; mouse.y = -1000; });
  init();
  draw();
})();

/* ============== SPOTLIGHT ============== */
(function() {
  const sp = document.getElementById('spotlight');
  document.addEventListener('mousemove', e => {
    sp.style.opacity = '1';
    sp.style.left = e.clientX + 'px';
    sp.style.top = e.clientY + 'px';
  });
  document.addEventListener('mouseleave', () => { sp.style.opacity = '0'; });
})();

/* ============== REVEAL ON SCROLL ============== */
(function() {
  const els = document.querySelectorAll('.reveal');
  const obs = new IntersectionObserver((entries) => {
    entries.forEach(e => {
      if (e.isIntersecting) {
        e.target.classList.add('visible');
        obs.unobserve(e.target);
      }
    });
  }, { threshold: 0.12 });
  els.forEach(el => obs.observe(el));
})();

/* ============== STATS COUNTERS ============== */
(function() {
  const counters = document.querySelectorAll('[data-count]');
  const obs = new IntersectionObserver((entries) => {
    entries.forEach(e => {
      if (e.isIntersecting) {
        const el = e.target;
        const target = parseInt(el.dataset.count, 10);
        const dur = 1400;
        const start = performance.now();
        function tick(now) {
          const t = Math.min(1, (now - start) / dur);
          const eased = 1 - Math.pow(1 - t, 3);
          el.textContent = Math.round(target * eased);
          if (t < 1) requestAnimationFrame(tick);
        }
        requestAnimationFrame(tick);
        obs.unobserve(el);
      }
    });
  }, { threshold: 0.5 });
  counters.forEach(c => obs.observe(c));
})();

/* ============== TERMINAL TYPEWRITER ============== */
(function() {
  const lastCheckEl = document.getElementById('status-last-check');
  if (!lastCheckEl) return;

  const startedAt = Date.now();
  const labels = {
    'pt-BR': { now: 'just now', sec: 'há %ss', min: 'há %sm' },
    'en-US': { now: 'just now', sec: '%ss ago', min: '%sm ago' },
  };
  const lang = (navigator.language || 'pt-BR').startsWith('pt') ? 'pt-BR' : 'en-US';
  const currentLabels = labels[lang];

  function update() {
    const elapsed = Math.floor((Date.now() - startedAt) / 1000);
    if (elapsed < 5) lastCheckEl.textContent = currentLabels.now;
    else if (elapsed < 60) lastCheckEl.textContent = currentLabels.sec.replace('%s', elapsed);
    else lastCheckEl.textContent = currentLabels.min.replace('%s', Math.floor(elapsed / 60));
  }

  update();
  setInterval(update, 1000);
})();

(function() {
  const term = document.getElementById('terminal');
  const lines = [
    { t: '<span class="t-comment"># 1) registrar usuário</span>', delay: 600 },
    { t: '<span class="t-shell">$</span> curl -X POST <span class="t-string">http://localhost:8080/api/v1/auth/register</span> \\', delay: 600 },
    { t: '    <span class="t-flag">-H</span> <span class="t-string">"Content-Type: application/json"</span> \\', delay: 400 },
    { t: '    <span class="t-flag">-d</span> <span class="t-string">\'{"email":"dev@egsys.com","password":"S3nh@F0rt3!2026"}\'</span>', delay: 700 },
    { t: '<span class="t-output">→ </span><span class="t-success">201 Created</span> <span class="t-output">{"id":"a3f9...","email":"dev@egsys.com"}</span>', delay: 900, plain: true },
    { t: '', delay: 200, plain: true },
    { t: '<span class="t-comment"># 2) autenticar e capturar JWT</span>', delay: 500 },
    { t: '<span class="t-shell">$</span> TOKEN=$(curl -s -X POST <span class="t-string">.../auth/login</span> | jq -r .accessToken)', delay: 800 },
    { t: '', delay: 200, plain: true },
    { t: '<span class="t-comment"># 3) criar tarefa autenticada</span>', delay: 500 },
    { t: '<span class="t-shell">$</span> curl -X POST <span class="t-string">http://localhost:8080/api/v1/tarefas</span> \\', delay: 600 },
    { t: '    <span class="t-flag">-H</span> <span class="t-string">"Authorization: Bearer $TOKEN"</span> \\', delay: 400 },
    { t: '    <span class="t-flag">-d</span> <span class="t-string">\'{"titulo":"Dev EGSYS","categoriaId":"...","dataHora":"2026-05-10T09:00:00Z"}\'</span>', delay: 700 },
    { t: '<span class="t-output">→ </span><span class="t-success">201 Created</span> <span class="t-output">· auditoria registrada · evento publicado</span>', delay: 1200, plain: true }
  ];

  let idx = 0;
  function next() {
    if (idx >= lines.length) {
      term.insertAdjacentHTML('beforeend', '<span class="cursor-blink"></span>');
      return;
    }
    const line = lines[idx++];
    const div = document.createElement('div');
    div.innerHTML = line.t || '&nbsp;';
    term.appendChild(div);
    term.scrollTop = term.scrollHeight;
    setTimeout(next, line.delay);
  }

  // start when terminal scrolls into view
  const obs = new IntersectionObserver((entries) => {
    entries.forEach(e => {
      if (e.isIntersecting) { next(); obs.unobserve(e.target); }
    });
  }, { threshold: 0.3 });
  obs.observe(term);
})();

/* ============== TECH STACK MARQUEE ============== */
(function() {
  const techs = [
    { n: 'Kotlin 2.0', c: 'p' },
    { n: 'Spring Boot 3.3', c: 'g' },
    { n: 'JVM 21 · Virtual Threads', c: 'o' },
    { n: 'PostgreSQL 16', c: 'c' },
    { n: 'Redis 7', c: 'o' },
    { n: 'Flyway', c: 'g' },
    { n: 'JWT RS256', c: 'o' },
    { n: 'Argon2id', c: 'p' },
    { n: 'Spring Security 6', c: 'g' },
    { n: 'JJWT', c: 'c' },
    { n: 'Bucket4j', c: 'o' },
    { n: 'Testcontainers', c: 'g' },
    { n: 'JUnit 5 · Kotest · MockK', c: 'p' },
    { n: 'Pitest', c: 'o' },
    { n: 'ArchUnit', c: 'c' },
    { n: 'JaCoCo', c: 'g' },
    { n: 'springdoc OpenAPI', c: 'o' },
    { n: 'Micrometer · Prometheus', c: 'p' },
    { n: 'OpenTelemetry', c: 'c' },
    { n: 'Logback JSON', c: 'g' },
    { n: 'Resilience4j', c: 'o' },
    { n: 'Trivy · Semgrep · gitleaks', c: 'p' },
    { n: 'GitHub Actions', c: 'c' },
    { n: 'Docker · distroless', c: 'g' }
  ];

  const track = document.getElementById('techTrack');
  function pill(t) {
    return `<div class="tech-pill"><span class="dot ${t.c}"></span>${t.n}</div>`;
  }
  // duplicate for seamless loop
  track.innerHTML = techs.map(pill).join('') + techs.map(pill).join('');
})();
